package com.kuxhausen.huemore.ui.registration;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;

import com.kuxhausen.huemore.MainActivity;
import com.kuxhausen.huemore.R;
import com.kuxhausen.huemore.network.GetBulbList;

public class RegistrationSuccessDialogFragment extends DialogFragment {
	MainActivity ma;

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		ma = (MainActivity) this.getActivity();
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder.setPositiveButton(R.string.accept,
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int id) {
						if (ma.bulbListenerFragment != null) {
							GetBulbList pushGroupMood = new GetBulbList(ma,
									ma.bulbListenerFragment);
							pushGroupMood.execute();
						}
					}
				});
		builder.setMessage(R.string.register_success);
		return builder.create();
	}
}
