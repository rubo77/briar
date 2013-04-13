package net.sf.briar.android.messages;

import static android.graphics.Typeface.BOLD;
import static android.view.Gravity.LEFT;
import static android.widget.LinearLayout.HORIZONTAL;
import static android.widget.LinearLayout.VERTICAL;
import static java.text.DateFormat.SHORT;
import static net.sf.briar.android.widgets.CommonLayoutParams.WRAP_WRAP_1;

import java.util.ArrayList;

import net.sf.briar.R;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.text.format.DateUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

class ConversationListAdapter extends ArrayAdapter<ConversationListItem>
implements OnItemClickListener {

	ConversationListAdapter(Context ctx) {
		super(ctx, android.R.layout.simple_expandable_list_item_1,
				new ArrayList<ConversationListItem>());
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		ConversationListItem item = getItem(position);
		Context ctx = getContext();
		Resources res = ctx.getResources();

		LinearLayout layout = new LinearLayout(ctx);
		layout.setOrientation(HORIZONTAL);
		if(item.getUnreadCount() > 0)
			layout.setBackgroundColor(res.getColor(R.color.unread_background));

		LinearLayout innerLayout = new LinearLayout(ctx);
		// Give me all the unused width
		innerLayout.setLayoutParams(WRAP_WRAP_1);
		innerLayout.setOrientation(VERTICAL);
		innerLayout.setGravity(LEFT);

		TextView name = new TextView(ctx);
		name.setTextSize(18);
		name.setMaxLines(1);
		name.setPadding(10, 10, 10, 10);
		int unread = item.getUnreadCount();
		String contactName = item.getContactName();
		if(unread > 0) name.setText(contactName + " (" + unread + ")");
		else name.setText(contactName);
		innerLayout.addView(name);

		if(item.isEmpty()) {
			TextView noMessages = new TextView(ctx);
			noMessages.setTextSize(14);
			noMessages.setPadding(10, 0, 10, 10);
			noMessages.setTextColor(res.getColor(R.color.no_messages));
			noMessages.setText(R.string.no_messages);
			innerLayout.addView(noMessages);
			layout.addView(innerLayout);
		} else {
			TextView subject = new TextView(ctx);
			subject.setTextSize(14);
			subject.setMaxLines(2);
			subject.setPadding(10, 0, 10, 10);
			if(unread > 0) subject.setTypeface(null, BOLD);
			String s = item.getSubject();
			subject.setText(s == null ? "" : s);
			innerLayout.addView(subject);
			layout.addView(innerLayout);

			TextView date = new TextView(ctx);
			date.setTextSize(14);
			date.setPadding(0, 10, 10, 10);
			long then = item.getTimestamp(), now = System.currentTimeMillis();
			date.setText(DateUtils.formatSameDayTime(then, now, SHORT, SHORT));
			layout.addView(date);
		}

		return layout;
	}

	public void onItemClick(AdapterView<?> parent, View view, int position,
			long id) {
		ConversationListItem item = getItem(position);
		Intent i = new Intent(getContext(), ConversationActivity.class);
		i.putExtra("net.sf.briar.CONTACT_ID", item.getContactId().getInt());
		i.putExtra("net.sf.briar.CONTACT_NAME", item.getContactName());
		i.putExtra("net.sf.briar.LOCAL_AUTHOR_ID",
				item.getLocalAuthorId().getBytes());
		getContext().startActivity(i);
	}
}
