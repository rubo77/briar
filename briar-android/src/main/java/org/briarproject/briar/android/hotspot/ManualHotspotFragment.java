package org.briarproject.briar.android.hotspot;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;

import javax.inject.Inject;

import androidx.annotation.Nullable;
import androidx.core.util.Consumer;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import static android.view.View.GONE;
import static org.briarproject.briar.android.AppModule.getAndroidComponent;
import static org.briarproject.briar.android.hotspot.AbstractTabsFragment.ARG_FOR_WIFI_CONNECT;
import static org.briarproject.briar.android.hotspot.HotspotState.HotspotStarted;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class ManualHotspotFragment extends Fragment {

	public final static String TAG = ManualHotspotFragment.class.getName();

	@Inject
	ViewModelProvider.Factory viewModelFactory;

	private HotspotViewModel viewModel;

	static ManualHotspotFragment newInstance(boolean forWifiConnect) {
		ManualHotspotFragment f = new ManualHotspotFragment();
		Bundle bundle = new Bundle();
		bundle.putBoolean(ARG_FOR_WIFI_CONNECT, forWifiConnect);
		f.setArguments(bundle);
		return f;
	}

	@Override
	public void onAttach(Context context) {
		super.onAttach(context);
		getAndroidComponent(requireContext()).inject(this);
		viewModel = new ViewModelProvider(requireActivity(), viewModelFactory)
				.get(HotspotViewModel.class);
	}

	@Override
	public View onCreateView(LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		return inflater
				.inflate(R.layout.fragment_hotspot_manual, container, false);
	}

	@Override
	public void onViewCreated(View v, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(v, savedInstanceState);

		TextView manualIntroView = v.findViewById(R.id.manualIntroView);
		TextView ssidLabelView = v.findViewById(R.id.ssidLabelView);
		TextView ssidView = v.findViewById(R.id.ssidView);
		TextView passwordView = v.findViewById(R.id.passwordView);
		TextView altView = v.findViewById(R.id.altView);

		Consumer<HotspotStarted> consumer;
		if (requireArguments().getBoolean(ARG_FOR_WIFI_CONNECT)) {
			manualIntroView.setText(R.string.hotspot_manual_wifi);
			ssidLabelView.setText(R.string.hotspot_manual_wifi_ssid);
			consumer = state -> {
				ssidView.setText(state.getNetworkConfig().ssid);
				passwordView.setText(state.getNetworkConfig().password);
			};
			altView.setText(R.string.hotspot_manual_wifi_alt);
		} else {
			manualIntroView.setText(R.string.hotspot_manual_site);
			ssidLabelView.setText(R.string.hotspot_manual_site_address);
			consumer = state -> ssidView.setText(state.getWebsiteConfig().url);
			altView.setText(R.string.hotspot_manual_site_alt);
			v.findViewById(R.id.passwordLabelView).setVisibility(GONE);
			passwordView.setVisibility(GONE);
		}
		viewModel.getState().observe(getViewLifecycleOwner(), state -> {
			// we only expect to be in this state here
			if (state instanceof HotspotStarted) {
				consumer.accept((HotspotStarted) state);
			}
		});
	}
}
