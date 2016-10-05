package org.briarproject.messaging;

import org.briarproject.api.FormatException;
import org.briarproject.api.clients.Client;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.clients.ContactGroupFactory;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.contact.ContactManager.AddContactHook;
import org.briarproject.api.contact.ContactManager.RemoveContactHook;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.data.MetadataParser;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.event.PrivateMessageReceivedEvent;
import org.briarproject.api.messaging.MessagingManager;
import org.briarproject.api.messaging.PrivateMessage;
import org.briarproject.api.messaging.PrivateMessageHeader;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;
import org.briarproject.api.sync.MessageStatus;
import org.briarproject.clients.BdfIncomingMessageHook;
import org.briarproject.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import javax.inject.Inject;

import static org.briarproject.clients.BdfConstants.MSG_KEY_READ;

class MessagingManagerImpl extends BdfIncomingMessageHook
		implements MessagingManager, Client, AddContactHook, RemoveContactHook {

	static final ClientId CLIENT_ID = new ClientId(StringUtils.fromHexString(
			"6bcdc006c0910b0f44e40644c3b31f1a"
					+ "8bf9a6d6021d40d219c86b731b903070"));

	private final ContactGroupFactory contactGroupFactory;

	@Inject
	MessagingManagerImpl(DatabaseComponent db, ClientHelper clientHelper,
			MetadataParser metadataParser,
			ContactGroupFactory contactGroupFactory) {
		super(db, clientHelper, metadataParser);
		this.contactGroupFactory = contactGroupFactory;
	}

	@Override
	public void createLocalState(Transaction txn) throws DbException {
		// Ensure we've set things up for any pre-existing contacts
		for (Contact c : db.getContacts(txn)) addingContact(txn, c);
	}

	@Override
	public void addingContact(Transaction txn, Contact c) throws DbException {
		try {
			// Create a group to share with the contact
			Group g = getContactGroup(c);
			// Return if we've already set things up for this contact
			if (db.containsGroup(txn, g.getId())) return;
			// Store the group and share it with the contact
			db.addGroup(txn, g);
			db.setVisibleToContact(txn, c.getId(), g.getId(), true);
			// Attach the contact ID to the group
			BdfDictionary d = new BdfDictionary();
			d.put("contactId", c.getId().getInt());
			clientHelper.mergeGroupMetadata(txn, g.getId(), d);
		} catch (FormatException e) {
			throw new RuntimeException(e);
		}
	}

	private Group getContactGroup(Contact c) {
		return contactGroupFactory.createContactGroup(CLIENT_ID, c);
	}

	@Override
	public void removingContact(Transaction txn, Contact c) throws DbException {
		db.removeGroup(txn, getContactGroup(c));
	}

	@Override
	public ClientId getClientId() {
		return CLIENT_ID;
	}

	@Override
	protected boolean incomingMessage(Transaction txn, Message m, BdfList body,
			BdfDictionary meta) throws DbException, FormatException {

		GroupId groupId = m.getGroupId();
		long timestamp = meta.getLong("timestamp");
		String contentType = meta.getString("contentType");
		boolean local = meta.getBoolean("local");
		boolean read = meta.getBoolean(MSG_KEY_READ);
		PrivateMessageHeader header = new PrivateMessageHeader(
				m.getId(), m.getGroupId(), timestamp, contentType, local, read,
				false, false);
		PrivateMessageReceivedEvent event = new PrivateMessageReceivedEvent(
				header, groupId);
		txn.attach(event);
		trackIncomingMessage(txn, m);

		// don't share message
		return false;
	}

	@Override
	public void addLocalMessage(PrivateMessage m) throws DbException {
		Transaction txn = db.startTransaction(false);
		try {
			BdfDictionary meta = new BdfDictionary();
			meta.put("timestamp", m.getMessage().getTimestamp());
			if (m.getParent() != null) meta.put("parent", m.getParent());
			meta.put("contentType", m.getContentType());
			meta.put("local", true);
			meta.put("read", true);
			clientHelper.addLocalMessage(txn, m.getMessage(), meta, true);
			trackOutgoingMessage(txn, m.getMessage());
			txn.setComplete();
		} catch (FormatException e) {
			throw new RuntimeException(e);
		} finally {
			db.endTransaction(txn);
		}
	}

	@Override
	public ContactId getContactId(GroupId g) throws DbException {
		try {
			BdfDictionary meta = clientHelper.getGroupMetadataAsDictionary(g);
			return new ContactId(meta.getLong("contactId").intValue());
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	@Override
	public GroupId getConversationId(ContactId c) throws DbException {
		Contact contact;
		Transaction txn = db.startTransaction(true);
		try {
			contact = db.getContact(txn, c);
			txn.setComplete();
		} finally {
			db.endTransaction(txn);
		}
		return getContactGroup(contact).getId();
	}

	@Override
	public Collection<PrivateMessageHeader> getMessageHeaders(ContactId c)
			throws DbException {
		Map<MessageId, BdfDictionary> metadata;
		Collection<MessageStatus> statuses;
		GroupId g;
		Transaction txn = db.startTransaction(true);
		try {
			g = getContactGroup(db.getContact(txn, c)).getId();
			metadata = clientHelper.getMessageMetadataAsDictionary(txn, g);
			statuses = db.getMessageStatus(txn, c, g);
			txn.setComplete();
		} catch (FormatException e) {
			throw new DbException(e);
		} finally {
			db.endTransaction(txn);
		}
		Collection<PrivateMessageHeader> headers =
				new ArrayList<PrivateMessageHeader>();
		for (MessageStatus s : statuses) {
			MessageId id = s.getMessageId();
			BdfDictionary meta = metadata.get(id);
			if (meta == null) continue;
			try {
				long timestamp = meta.getLong("timestamp");
				String contentType = meta.getString("contentType");
				boolean local = meta.getBoolean("local");
				boolean read = meta.getBoolean("read");
				headers.add(new PrivateMessageHeader(id, g, timestamp,
						contentType, local, read, s.isSent(), s.isSeen()));
			} catch (FormatException e) {
				throw new DbException(e);
			}
		}
		return headers;
	}

	@Override
	public byte[] getMessageBody(MessageId m) throws DbException {
		try {
			// Parent ID, content type, private message body
			BdfList message = clientHelper.getMessageAsList(m);
			return message.getRaw(2);
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

}
