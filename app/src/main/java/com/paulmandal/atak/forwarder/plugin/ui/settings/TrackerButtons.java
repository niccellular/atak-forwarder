package com.paulmandal.atak.forwarder.plugin.ui.settings;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Handler;
import android.preference.Preference;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.constraintlayout.widget.ConstraintLayout;

import com.atakmap.android.gui.EditText;
import com.atakmap.android.gui.PanListPreference;
import com.atakmap.android.gui.PluginSpinner;
import com.geeksville.mesh.MeshProtos;
import com.google.gson.Gson;
import com.paulmandal.atak.forwarder.R;
import com.paulmandal.atak.forwarder.comm.commhardware.MeshtasticCommHardware;
import com.paulmandal.atak.forwarder.comm.commhardware.MeshtasticDeviceSwitcher;
import com.paulmandal.atak.forwarder.comm.commhardware.meshtastic.MeshtasticDevice;
import com.paulmandal.atak.forwarder.nonatak.NonAtakMeshtasticConfigurator;
import com.paulmandal.atak.forwarder.persistence.PreferencesDefaults;
import com.paulmandal.atak.forwarder.persistence.PreferencesKeys;
import com.paulmandal.atak.forwarder.plugin.ui.EditTextValidator;

import java.util.List;

public class TrackerButtons {
    public TrackerButtons(Context settingsMenuContext,
                          Context pluginContext,
                          Handler uiThreadHandler,
                          DevicesList devicesList,
                          MeshtasticCommHardware meshtasticCommHardware,
                          Preference teams,
                          Preference roles,
                          Preference writeToDevice) {
        PanListPreference teamsListPreference = (PanListPreference) teams;
        teamsListPreference.setEntries(R.array.teams);
        teamsListPreference.setEntryValues(R.array.teams_values);

        PanListPreference rolesListPreference = (PanListPreference) roles;
        rolesListPreference.setEntries(R.array.roles);
        rolesListPreference.setEntryValues(R.array.roles_values);

        writeToDevice.setOnPreferenceClickListener((Preference preference) -> {
            SharedPreferences sharedPreferences = preference.getSharedPreferences();

            Gson gson = new Gson();
            List<MeshtasticDevice> meshtasticDevices = devicesList.getMeshtasticDevices();
            MeshtasticDevice commDevice = gson.fromJson(sharedPreferences.getString(PreferencesKeys.KEY_SET_COMM_DEVICE, PreferencesDefaults.DEFAULT_COMM_DEVICE), MeshtasticDevice.class);

            meshtasticDevices.remove(commDevice);

            // TODO: validate that there are comm devices, or Toast and say we need to bluetooth/USB to some

            // Create view
            ConstraintLayout dialogLayout = (ConstraintLayout) LayoutInflater.from(pluginContext).inflate(R.layout.tracker_dialog_layout, null);
            PluginSpinner devicesSpinner = dialogLayout.findViewById(R.id.spinner_devices);
            ArrayAdapter<MeshtasticDevice> devicesDataAdapter = new ArrayAdapter<>(pluginContext, R.layout.plugin_spinner_item, meshtasticDevices);
            devicesDataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            devicesSpinner.setAdapter(devicesDataAdapter);

            EditText callsignEditText = dialogLayout.findViewById(R.id.edittext_device_callsign);
            callsignEditText.addTextChangedListener(new EditTextValidator(callsignEditText) {
                @Override
                public void validate(TextView textView, String text) {
                    if (text == null || text.isEmpty()) {
                        textView.setError("You must enter a device callsign");
                        return;
                    }

                    textView.setError(null);
                }
            });

            ProgressBar progressBar = dialogLayout.findViewById(R.id.progressbar_writing_to_device);

            final AlertDialog.Builder alertDialog = new AlertDialog.Builder(settingsMenuContext)
                    .setTitle(pluginContext.getResources().getString(R.string.choose_device_and_callsign))
                    .setView(dialogLayout)
                    .setPositiveButton(pluginContext.getResources().getString(R.string.ok), (DialogInterface dialog, int whichButton) -> {
                    })
                    .setNegativeButton(pluginContext.getResources().getString(R.string.cancel), (DialogInterface dialog, int whichButton) -> dialog.cancel());

            AlertDialog dialog = alertDialog.show();

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener((View v) -> {
                if (devicesSpinner.getSelectedItemPosition() == AdapterView.INVALID_POSITION) {
                    Toast.makeText(settingsMenuContext, "You must pick a device to write to!", Toast.LENGTH_SHORT).show();
                    return;
                } else if (callsignEditText.getText().toString().equals("")) {
                    Toast.makeText(settingsMenuContext, "You must enter a callsign for the device!", Toast.LENGTH_SHORT).show();
                    return;
                }

                MeshtasticDevice targetDevice = (MeshtasticDevice) devicesSpinner.getAdapter().getItem(devicesSpinner.getSelectedItemPosition());
                String callsign = callsignEditText.getText().toString();

                String channelName = sharedPreferences.getString(PreferencesKeys.KEY_CHANNEL_NAME, PreferencesDefaults.DEFAULT_CHANNEL_NAME);
                MeshProtos.ChannelSettings.ModemConfig channelMode = MeshProtos.ChannelSettings.ModemConfig.forNumber(Integer.parseInt(sharedPreferences.getString(PreferencesKeys.KEY_CHANNEL_MODE, PreferencesDefaults.DEFAULT_CHANNEL_MODE)));
                byte[] psk = Base64.decode(sharedPreferences.getString(PreferencesKeys.KEY_CHANNEL_PSK, PreferencesDefaults.DEFAULT_CHANNEL_PSK), Base64.DEFAULT);

                int teamIndex = Integer.parseInt(sharedPreferences.getString(PreferencesKeys.KEY_TRACKER_TEAM, PreferencesDefaults.DEFAULT_TRACKER_TEAM));
                int roleIndex = Integer.parseInt(sharedPreferences.getString(PreferencesKeys.KEY_TRACKER_ROLE, PreferencesDefaults.DEFAULT_TRACKER_ROLE));
                int pliIntervalS = Integer.parseInt(sharedPreferences.getString(PreferencesKeys.KEY_TRACKER_PLI_INTERVAL, PreferencesDefaults.DEFAULT_TRACKER_PLI_INTERVAL));
                int screenShutoffDelayS = Integer.parseInt(sharedPreferences.getString(PreferencesKeys.KEY_TRACKER_SCREEN_OFF_TIME, PreferencesDefaults.DEFAULT_TRACKER_SCREEN_OFF_TIME));

                writeToDevice(settingsMenuContext, uiThreadHandler, meshtasticCommHardware, commDevice, targetDevice, callsign, channelName, psk, channelMode, teamIndex, roleIndex, pliIntervalS, screenShutoffDelayS, () -> {
                    progressBar.setVisibility(View.GONE);
                    dialog.dismiss();
                });
                progressBar.setVisibility(View.VISIBLE);
            });
            return true;
        });
    }

    private void writeToDevice(Context settingsMenuContext,
                               Handler uiThreadHandler,
                               MeshtasticCommHardware meshtasticCommHardware,
                               MeshtasticDevice commDevice,
                               MeshtasticDevice targetDevice,
                               String deviceCallsign,
                               String channelName,
                               byte[] psk,
                               MeshProtos.ChannelSettings.ModemConfig modemConfig,
                               int teamIndex,
                               int roleIndex,
                               int pliIntervalS,
                               int screenShutoffDelayS,
                               NonAtakMeshtasticConfigurator.Listener listener) {
        MeshtasticDeviceSwitcher meshtasticDeviceSwitcher = new MeshtasticDeviceSwitcher(settingsMenuContext);
        NonAtakMeshtasticConfigurator nonAtakMeshtasticConfigurator = new NonAtakMeshtasticConfigurator(settingsMenuContext, uiThreadHandler, meshtasticCommHardware, meshtasticDeviceSwitcher, commDevice, targetDevice, deviceCallsign, channelName, psk, modemConfig, teamIndex, roleIndex, pliIntervalS, screenShutoffDelayS, listener);
        nonAtakMeshtasticConfigurator.writeToDevice();
    }
}
