package org.briarproject.briar.android.contact;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.TextInputEditText;
import android.support.design.widget.TextInputLayout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.fragment.BaseFragment;

import java.util.regex.Matcher;

import javax.annotation.Nullable;

import static android.content.Context.CLIPBOARD_SERVICE;
import static android.content.Intent.ACTION_SEND;
import static android.content.Intent.EXTRA_TEXT;
import static android.os.Build.VERSION.SDK_INT;
import static android.support.v4.graphics.drawable.DrawableCompat.setTint;
import static android.support.v4.graphics.drawable.DrawableCompat.wrap;
import static android.widget.Toast.LENGTH_SHORT;
import static java.util.Objects.requireNonNull;
import static org.briarproject.briar.android.contact.ContactLinkExchangeActivity.LINK_REGEX;
import static org.briarproject.briar.android.contact.ContactLinkExchangeActivity.OUR_LINK;
import static org.briarproject.briar.android.util.UiUtils.resolveColorAttribute;
import static org.briarproject.briar.android.util.UiUtils.setError;

public class ContactLinkExchangeFragment extends BaseFragment {

	static final String TAG = ContactLinkExchangeFragment.class.getName();

	static BaseFragment newInstance(@Nullable String link) {
		BaseFragment f = new ContactLinkExchangeFragment();
		Bundle bundle = new Bundle();
		bundle.putString("link", link);
		f.setArguments(bundle);
		return f;
	}

	private ClipboardManager clipboard;
	private TextInputLayout linkInputLayout, contactNameLayout;
	private TextInputEditText linkInput, contactNameInput;
	private Button addButton;

	@Override
	public String getUniqueTag() {
		return TAG;
	}

	@Override
	public void injectFragment(ActivityComponent component) {
		component.inject(this);
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		if (getActivity() == null || getContext() == null) return null;

		getActivity().setTitle(R.string.add_contact_title);

		View v = inflater.inflate(R.layout.fragment_contact_link_exchange,
				container, false);

		clipboard = (ClipboardManager) requireNonNull(
				getContext().getSystemService(CLIPBOARD_SERVICE));

		int color =
				resolveColorAttribute(getContext(), R.attr.colorControlNormal);

		addButton = v.findViewById(R.id.addButton);
		addButton.setOnClickListener(view -> onAddButtonClicked());

		contactNameLayout = v.findViewById(R.id.contactNameLayout);
		contactNameInput = v.findViewById(R.id.contactNameInput);
		if (SDK_INT < 23) {
			Drawable drawable =
					wrap(contactNameInput.getCompoundDrawables()[0]);
			setTint(drawable, color);
			contactNameInput.setCompoundDrawables(drawable, null, null, null);
		}

		linkInputLayout = v.findViewById(R.id.linkInputLayout);
		linkInput = v.findViewById(R.id.linkInput);
		if (SDK_INT < 23) {
			Drawable drawable = wrap(linkInput.getCompoundDrawables()[0]);
			setTint(drawable, color);
			linkInput.setCompoundDrawables(drawable, null, null, null);
		}
		if (getArguments() != null)
			linkInput.setText(getArguments().getString("link"));

		Button pasteButton = v.findViewById(R.id.pasteButton);
		pasteButton.setOnClickListener(view -> {
			ClipData clip = clipboard.getPrimaryClip();
			if (clip != null)
				linkInput.setText(clip.getItemAt(0).getText());
		});

		Button scanCodeButton = v.findViewById(R.id.scanCodeButton);
		scanCodeButton.setOnClickListener(view -> {
			ContactLinkExchangeActivity activity = getCastActivity();
			if (activity != null) activity.scanCode();
		});

		TextView linkView = v.findViewById(R.id.linkView);
		linkView.setText(OUR_LINK);

		ClipData clip = ClipData.newPlainText(
				getString(R.string.link_clip_label), OUR_LINK);

		Button copyButton = v.findViewById(R.id.copyButton);
		copyButton.setOnClickListener(view -> {
			clipboard.setPrimaryClip(clip);
			Toast.makeText(getContext(), R.string.link_copied_toast,
					LENGTH_SHORT).show();
		});

		Button shareButton = v.findViewById(R.id.shareButton);
		shareButton.setOnClickListener(view -> {
			Intent i = new Intent(ACTION_SEND);
			i.putExtra(EXTRA_TEXT, OUR_LINK);
			i.setType("text/plain");
			startActivity(i);
		});

		Button showCodeButton = v.findViewById(R.id.showCodeButton);
		showCodeButton.setOnClickListener(
				view -> {
					ContactLinkExchangeActivity activity = getCastActivity();
					if (activity != null) activity.showCode();
				});

		return v;
	}

	private ContactLinkExchangeActivity getCastActivity() {
		return (ContactLinkExchangeActivity) getActivity();
	}

	private boolean isInputError() {
		boolean briarLink = isBriarLink(linkInput.getText());
		if (!briarLink) {
			linkInputLayout.setError("Invalid link");
			return true;
		} else linkInputLayout.setError(null);
		String link = getLink();
		boolean isOurLink = link != null && OUR_LINK.equals("briar://" + link);
		if (isOurLink) {
			linkInputLayout.setError("Add your peer's link, not your own.");
			return true;
		} else linkInputLayout.setError(null);
		boolean validContactName = contactNameInput.getText() != null &&
				contactNameInput.getText().length() > 0;
		if (!validContactName) {
			contactNameLayout.setError("Nickname is missing");
			return true;
		} else contactNameLayout.setError(null);
		setError(linkInputLayout, null, false);
		return false;
	}

	private boolean isBriarLink(@Nullable CharSequence s) {
		ContactLinkExchangeActivity activity = getCastActivity();
		return activity != null && activity.isBriarLink(s);
	}

	@Nullable
	private String getLink() {
		Matcher matcher = LINK_REGEX.matcher(linkInput.getText());
		if (matcher.matches()) // needs to be called before groups become available
			return matcher.group(2);
		else
			return null;
	}

	private void onAddButtonClicked() {
		ContactLinkExchangeActivity activity = getCastActivity();
		if (activity == null || isInputError()) return;

		String linkText = getLink();
		if (linkText == null) throw new AssertionError();
		activity.addFakeRequest(contactNameInput.getText().toString(),
				linkText);

		Intent intent = new Intent(activity, PendingRequestsActivity.class);
		startActivity(intent);
		finish();
	}
}