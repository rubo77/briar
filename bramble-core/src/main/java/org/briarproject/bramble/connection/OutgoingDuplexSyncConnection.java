package org.briarproject.bramble.connection;

import org.briarproject.bramble.api.connection.ConnectionRegistry;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.HandshakeManager;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.duplex.DuplexTransportConnection;
import org.briarproject.bramble.api.properties.TransportPropertyManager;
import org.briarproject.bramble.api.sync.Priority;
import org.briarproject.bramble.api.sync.PriorityHandler;
import org.briarproject.bramble.api.sync.SyncSession;
import org.briarproject.bramble.api.sync.SyncSessionFactory;
import org.briarproject.bramble.api.transport.KeyManager;
import org.briarproject.bramble.api.transport.StreamContext;
import org.briarproject.bramble.api.transport.StreamReaderFactory;
import org.briarproject.bramble.api.transport.StreamWriter;
import org.briarproject.bramble.api.transport.StreamWriterFactory;

import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.util.concurrent.Executor;

import static java.util.logging.Level.WARNING;
import static org.briarproject.bramble.api.sync.SyncConstants.PRIORITY_NONCE_BYTES;
import static org.briarproject.bramble.util.LogUtils.logException;

@NotNullByDefault
class OutgoingDuplexSyncConnection extends DuplexSyncConnection
		implements Runnable {

	// FIXME: Exchange timestamp as part of handshake protocol?
	private static final long TIMESTAMP = 1617235200; // 1 April 2021 00:00 UTC

	private final SecureRandom secureRandom;
	private final HandshakeManager handshakeManager;
	private final ContactId contactId;

	OutgoingDuplexSyncConnection(
			KeyManager keyManager,
			ConnectionRegistry connectionRegistry,
			StreamReaderFactory streamReaderFactory,
			StreamWriterFactory streamWriterFactory,
			SyncSessionFactory syncSessionFactory,
			TransportPropertyManager transportPropertyManager,
			Executor ioExecutor,
			SecureRandom secureRandom,
			HandshakeManager handshakeManager,
			ContactId contactId,
			TransportId transportId,
			DuplexTransportConnection connection) {
		super(keyManager, connectionRegistry, streamReaderFactory,
				streamWriterFactory, syncSessionFactory,
				transportPropertyManager, ioExecutor, transportId, connection);
		this.secureRandom = secureRandom;
		this.handshakeManager = handshakeManager;
		this.contactId = contactId;
	}

	@Override
	public void run() {
		LOG.info("Running OutgoingDuplexSyncConnection on transport " +
				transportId.getString());
		// Allocate a stream context

		StreamContext ctx = allocateStreamContext(contactId, transportId);
		if (ctx == null) {
			LOG.warning("Could not allocate stream context");
			onWriteError();
			return;
		}
		if (ctx.isHandshakeMode()) {
			LOG.info("OutgoingDuplexSyncConnection - context is in handshake mode, performing handshake");
			if (!performHandshake(ctx)) {
				LOG.warning("Handshake failed");
				return;
			}
			// Allocate a rotation mode stream context
			ctx = allocateStreamContext(contactId, transportId);
			if (ctx == null) {
				LOG.warning("Could not allocate stream context");
				onWriteError();
				return;
			}
			if (ctx.isHandshakeMode()) {
				LOG.warning("Got handshake mode context after handshaking");
				onWriteError();
				return;
			}
		}
		// Start the incoming session on another thread
		Priority priority = generatePriority();
		ioExecutor.execute(() -> runIncomingSession(priority));
		try {
			// Create and run the outgoing session
			SyncSession out =
					createDuplexOutgoingSession(ctx, writer, priority);
			setOutgoingSession(out);
			out.run();
			writer.dispose(false);
		} catch (IOException e) {
			logException(LOG, WARNING, e);
			onWriteError();
		}
	}

	private void runIncomingSession(Priority priority) {
		// Read and recognise the tag
		StreamContext ctx = recogniseTag(reader, transportId);
		// Unrecognised tags are suspicious in this case
		if (ctx == null) {
			LOG.warning("Unrecognised tag for returning stream");
			onReadError();
			return;
		}
		// Check that the stream comes from the expected contact
		ContactId inContactId = ctx.getContactId();
		if (inContactId == null) {
			LOG.warning("Expected contact tag, got rendezvous tag");
			onReadError();
			return;
		}
		if (!contactId.equals(inContactId)) {
			LOG.warning("Wrong contact ID for returning stream");
			onReadError();
			return;
		}
		if (ctx.isHandshakeMode()) {
			// TODO: Support handshake mode for contacts
			LOG.warning("Received handshake tag, expected rotation mode");
			onReadError();
			return;
		}
		connectionRegistry.registerOutgoingConnection(contactId, transportId,
				this, priority);
		try {
			// Store any transport properties discovered from the connection
			transportPropertyManager.addRemotePropertiesFromConnection(
					contactId, transportId, remote);
			// We don't expect to receive a priority for this connection
			PriorityHandler handler = p ->
					LOG.info("Ignoring priority for outgoing connection");
			// Create and run the incoming session
			createIncomingSession(ctx, reader, handler).run();
			reader.dispose(false, true);
			interruptOutgoingSession();
			connectionRegistry.unregisterConnection(contactId, transportId,
					this, false, false);
		} catch (DbException | IOException e) {
			logException(LOG, WARNING, e);
			onReadError();
			connectionRegistry.unregisterConnection(contactId, transportId,
					this, false, true);
		}
	}

	private boolean performHandshake(StreamContext ctxOut) {
		LOG.info("Performing handshake (Outgoing) for transport " +
				ctxOut.getTransportId().getString());
		// Flush the output stream to send the outgoing stream header
		StreamWriter out;
		try {
			out = streamWriterFactory.createStreamWriter(
					writer.getOutputStream(), ctxOut);
			out.getOutputStream().flush();
		} catch (IOException e) {
			logException(LOG, WARNING, e);
			onWriteError();
			return false;
		}
		// Read and recognise the tag
		StreamContext ctxIn = recogniseTag(reader, transportId);
		// Unrecognised tags are suspicious in this case
		if (ctxIn == null) {
			LOG.warning("Unrecognised tag for returning stream");
			onReadError();
			return false;
		}
		// Check that the stream comes from the expected contact
		ContactId inContactId = ctxIn.getContactId();
		if (inContactId == null) {
			LOG.warning("Expected contact tag, got rendezvous tag");
			onReadError();
			return false;
		}
		if (!inContactId.equals(contactId)) {
			LOG.warning("Wrong contact ID for returning stream");
			onReadError();
			return false;
		}
		// TODO: Register the connection, close it if it's redundant
		// Handshake and exchange contacts
		try {
			InputStream in = streamReaderFactory.createStreamReader(
					reader.getInputStream(), ctxIn);
			HandshakeManager.HandshakeResult result =
					handshakeManager.handshake(contactId, in, out);
			keyManager.addRotationKeys(contactId, result.getMasterKey(),
					TIMESTAMP, result.isAlice(), true);
			LOG.info("Rotation keys added - OutgoingDuplexSyncConnection");
			return true;
		} catch (IOException | DbException e) {
			logException(LOG, WARNING, e);
			onWriteError();
			return false;
		} finally {
			// TODO: Unregister the connection
		}
	}

	private void onReadError() {
		// 'Recognised' is always true for outgoing connections
		onReadError(true);
	}

	private Priority generatePriority() {
		byte[] nonce = new byte[PRIORITY_NONCE_BYTES];
		secureRandom.nextBytes(nonce);
		return new Priority(nonce);
	}
}
