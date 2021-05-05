package org.briarproject.briar;

import org.briarproject.briar.attachment.AttachmentModule;
import org.briarproject.briar.avatar.AvatarModule;
import org.briarproject.briar.blog.BlogModule;
import org.briarproject.briar.client.BriarClientModule;
import org.briarproject.briar.feed.DnsModule;
import org.briarproject.briar.feed.FeedModule;
import org.briarproject.briar.forum.ForumModule;
import org.briarproject.briar.identity.IdentityModule;
import org.briarproject.briar.introduction.IntroductionModule;
import org.briarproject.briar.messaging.MessagingModule;
import org.briarproject.briar.privategroup.PrivateGroupModule;
import org.briarproject.briar.privategroup.invitation.GroupInvitationModule;
import org.briarproject.briar.remotewipe.RemoteWipeModule;
import org.briarproject.briar.sharing.SharingModule;
import org.briarproject.briar.socialbackup.SocialBackupModule;
import org.briarproject.briar.test.TestModule;

import dagger.Module;

@Module(includes = {
		AvatarModule.class,
		BlogModule.class,
		BriarClientModule.class,
		FeedModule.class,
		DnsModule.class,
		ForumModule.class,
		GroupInvitationModule.class,
		IdentityModule.class,
		IntroductionModule.class,
		AttachmentModule.class,
		MessagingModule.class,
		PrivateGroupModule.class,
		RemoteWipeModule.class,
		SharingModule.class,
		SocialBackupModule.class,
		TestModule.class
})
public class BriarCoreModule {
}
