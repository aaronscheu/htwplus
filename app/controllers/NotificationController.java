package controllers;

import java.util.LinkedList;
import java.util.List;

import managers.NotificationManager;
import models.Account;
import models.Notification;
import play.Play;
import play.twirl.api.Html;
import play.db.jpa.Transactional;
import play.i18n.Messages;
import play.mvc.Result;
import play.mvc.Security;
import views.html.Notification.view;

import javax.inject.Inject;

@Transactional
@Security.Authenticated(Secured.class)
public class NotificationController extends BaseController {

    @Inject
    NotificationManager notificationManager;

    final int LIMIT = Integer.parseInt(Play.application().configuration().getString("htwplus.notification.limit"));

    /**
     * @deprecated Deprecated since refactor of notification system
     * @return Html
     */
	public Html view() {
		return getNotifications();
	}

    /**
     * Returns all notifications for current user.
     *
     * @return Html rendered instance
     */
    @Transactional(readOnly = true)
	public static Html getNotifications() {
		Account account = Component.currentAccount();
		
		if (account == null) {
			return new Html("Das wird nichts");
		}

        List<Notification> list = null;
        try {
            list = NotificationManager.findByAccountIdUnread(account.id);
        } catch (Throwable throwable) { throwable.printStackTrace(); }

        List<Integer> countedNotifications = NotificationController.countNotifications(list);
        return views.html.Notification.menuitem.render(list, countedNotifications.get(0),
                NotificationManager.countUnreadNotificationsForAccountId(account.id)
        );
	}

    /**
     * Redirects to the target URL of the notification, if user has access.
     *
     * @param notificationId ID of the notification
     * @return SimpleResult with redirection
     */
    @Transactional
	public Result forward(Long notificationId) {
		Notification notification = notificationManager.findById(notificationId);

		if (notification == null) {
            flash("info", Messages.get("notification.obsolete_notification"));
            return Secured.nullRedirect(request());
		}
		
		if (!Secured.hasAccessToNotification(notification)) {
            flash("error", Messages.get("error.access_denied"));
			return redirect(controllers.routes.Application.index());
		}

        // update only when notification is unread
        if (!notification.isRead) {
            notification.isRead = true;
            notificationManager.update(notification);
        }

		return redirect(notification.targetUrl);
	}

    /**
     * Shows all notifications.
     *
     * @param page Current page
     * @return Result
     */
    @Transactional(readOnly = true)
    public Result showAll(int page) {
        List<Notification> notifications = null;
        try {
            notifications = notificationManager.findByAccountIdForPage(Component.currentAccount().id, LIMIT, page);
        } catch (Throwable throwable) { throwable.printStackTrace(); }

        List<Integer> countedNotifications = NotificationController.countNotifications(notifications);
        Navigation.set(Navigation.Level.NOTIFICATIONS, Messages.get("notification.title"));
        return ok(view.render(notifications, LIMIT, page,
                notificationManager.countNotificationsForAccountId(Component.currentAccount().id), countedNotifications.get(1)
        ));
    }

    /**
     * Loops over a notification list and counts:
     * 1st element (index 0): All notifications
     * 2nd element (index 1): Unread notifications
     * 3rd element (index 2): Read notifications
     *
     * @param notifications List of notifications
     * @return List with count of all, unread and read notifications
     */
    public static List<Integer> countNotifications(List<Notification> notifications) {
        int countAll = 0;
        int countUnread = 0;
        int countRead = 0;
        List<Integer> count = new LinkedList<>();

        if (notifications != null) {
            countAll = notifications.size();
            for (Notification notification : notifications) {
                if (notification.isRead) {
                    countRead++;
                }
            }
            countUnread = countAll - countRead;
        }

        // add counted elements to list
        count.add(0, countAll);
        count.add(1, countUnread);
        count.add(2, countRead);

        return count;
    }

    /**
     * Marks all notifications as read for an Account.
     *
     * @return Result
     */
    @Transactional
    public Result readAll() {
        notificationManager.markAllAsRead(Component.currentAccount());
        flash("success", Messages.get("notification.read_everything_ok"));

        return redirect(request().getHeader("referer"));
    }
}
