package org.briarproject.android.blogs;

import org.briarproject.android.api.AndroidNotificationManager;
import org.briarproject.android.controller.handler.ResultExceptionHandler;
import org.briarproject.api.blogs.Blog;
import org.briarproject.api.blogs.BlogManager;
import org.briarproject.api.db.DatabaseExecutor;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.NoSuchGroupException;
import org.briarproject.api.db.NoSuchMessageException;
import org.briarproject.api.event.BlogPostAddedEvent;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.event.GroupAddedEvent;
import org.briarproject.api.event.GroupRemovedEvent;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.lifecycle.LifecycleManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.api.blogs.BlogManager.CLIENT_ID;

class FeedControllerImpl extends BaseControllerImpl
		implements FeedController {

	private static final Logger LOG =
			Logger.getLogger(FeedControllerImpl.class.getName());

	private volatile FeedListener listener;

	@Inject
	FeedControllerImpl(@DatabaseExecutor Executor dbExecutor,
			LifecycleManager lifecycleManager, EventBus eventBus,
			AndroidNotificationManager notificationManager,
			IdentityManager identityManager, BlogManager blogManager) {
		super(dbExecutor, lifecycleManager, eventBus, notificationManager,
				identityManager, blogManager);
	}

	@Override
	public void onStart() {
		super.onStart();
		if (listener == null) throw new IllegalStateException();
		notificationManager.blockAllBlogPostNotifications();
		notificationManager.clearAllBlogPostNotifications();
	}

	@Override
	public void onStop() {
		super.onStop();
		notificationManager.unblockAllBlogPostNotifications();
	}

	@Override
	public void setFeedListener(FeedListener listener) {
		super.setBlogListener(listener);
		this.listener = listener;
	}

	@Override
	public void eventOccurred(Event e) {
		if (e instanceof BlogPostAddedEvent) {
			BlogPostAddedEvent b = (BlogPostAddedEvent) e;
			LOG.info("Blog post added");
			onBlogPostAdded(b.getHeader(), b.isLocal());
		} else if (e instanceof GroupAddedEvent) {
			GroupAddedEvent g = (GroupAddedEvent) e;
			if (g.getGroup().getClientId().equals(CLIENT_ID)) {
				LOG.info("Blog added");
				onBlogAdded();
			}
		} else if (e instanceof GroupRemovedEvent) {
			GroupRemovedEvent g = (GroupRemovedEvent) e;
			if (g.getGroup().getClientId().equals(CLIENT_ID)) {
				LOG.info("Blog removed");
				onBlogRemoved();
			}
		}
	}

	private void onBlogAdded() {
		listener.runOnUiThreadUnlessDestroyed(new Runnable() {
			@Override
			public void run() {
				listener.onBlogAdded();
			}
		});
	}

	@Override
	public void loadBlogPosts(
			final ResultExceptionHandler<Collection<BlogPostItem>, DbException> handler) {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					long now = System.currentTimeMillis();
					Collection<BlogPostItem> posts = new ArrayList<>();
					for (Blog b : blogManager.getBlogs()) {
						try {
							posts.addAll(loadItems(b.getId()));
						} catch (NoSuchGroupException | NoSuchMessageException e) {
							if (LOG.isLoggable(WARNING))
								LOG.log(WARNING, e.toString(), e);
						}
					}
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Loading all posts took " + duration + " ms");
					handler.onResult(posts);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					handler.onException(e);
				}
			}
		});
	}

	@Override
	public void loadPersonalBlog(
			final ResultExceptionHandler<Blog, DbException> handler) {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					long now = System.currentTimeMillis();
					Author a = identityManager.getLocalAuthor();
					Blog b = blogManager.getPersonalBlog(a);
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Loading blog took " + duration + " ms");
					handler.onResult(b);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					handler.onException(e);
				}
			}
		});
	}

}
