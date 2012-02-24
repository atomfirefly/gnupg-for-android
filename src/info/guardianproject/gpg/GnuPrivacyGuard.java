package info.guardianproject.gpg;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View.OnCreateContextMenuListener;
import android.widget.ScrollView;
import android.widget.TextView;

public class GnuPrivacyGuard extends Activity implements OnCreateContextMenuListener {
	public static final String TAG = "gpg";
	
	private ScrollView consoleScroll;
	private TextView consoleText;
	
	public static final String LOG_UPDATE = "LOG_UPDATE";
	public static final String COMMAND_FINISHED = "COMMAND_FINISHED";

	private CommandThread commandThread;
	private StringBuffer log;
	private BroadcastReceiver logUpdateReceiver;
	private BroadcastReceiver commandFinishedReceiver;
	public String command;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
		NativeHelper.setup(getApplicationContext());
		NativeHelper.unpackAssets(getApplicationContext());
		// TODO figure out how to manage upgrades, etc.

        setContentView(R.layout.main);
		consoleScroll = (ScrollView) findViewById(R.id.consoleScroll);
		consoleText = (TextView) findViewById(R.id.consoleText);
		
		log = new StringBuffer();
    }

	@Override
	protected void onResume() {
		super.onResume();
		registerReceivers();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.options_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_list_keys:
			command = "./gpg2 --fixed-list-mode --with-colons --list-keys";
			commandThread = new CommandThread();
			commandThread.start();
			return true;
		case R.id.menu_run_test:
			command = "./gpg2 --version";
			commandThread = new CommandThread();
			commandThread.start();
			return true;
		case R.id.menu_gen_key:
			String batch = "%echo Generating a basic OpenPGP key\nKey-Type: DSA\nKey-Length: 1024\nSubkey-Type: ELG-E\nSubkey-Length: 1024\nName-Real: Test Key\nName-Comment: for testing only\nName-Email: test@gpg.guardianproject.info\nExpire-Date: 0\n%no-ask-passphrase\n%no-protection\n%commit\n%echo done";
			File batchfile = new File(getCacheDir(), "batch.txt");
			try {
				FileWriter outFile = new FileWriter(batchfile);
				PrintWriter out = new PrintWriter(outFile);
				out.println(batch);
				out.close();
			}  catch (Exception e) {
				Log.e(GnuPrivacyGuard.TAG, "Error!!!", e);
				return false;
			} 
			command = "./gpg2 --batch --no-tty --gen-key " + batchfile.getAbsolutePath();
			commandThread = new CommandThread();
			commandThread.start();
			return true;
		}
		return false;
	}

	private void updateLog() {
		final String logContents = log.toString();
		if (logContents != null && logContents.trim().length() > 0)
			consoleText.setText(logContents);
		consoleScroll.scrollTo(0, consoleText.getHeight());
	}

	class CommandThread extends Thread {
		private LogUpdate logUpdate;

		@Override
		public void run() {
			logUpdate = new LogUpdate();
			try {
				String envp[] = {"HOME=" + NativeHelper.app_home, 
				"LD_LIBRARY_PATH=/system/lib:" + NativeHelper.app_opt + "/lib"};
				File dir = new File(NativeHelper.app_opt, "bin");
				Process sh = Runtime.getRuntime().exec("/system/bin/sh", envp, dir);
				OutputStream os = sh.getOutputStream();

				StreamThread it = new StreamThread(sh.getInputStream(), logUpdate);
				StreamThread et = new StreamThread(sh.getErrorStream(), logUpdate);

				it.start();
				et.start();
				
				Log.i(GnuPrivacyGuard.TAG, command);
				writeCommand(os, command);
				writeCommand(os, "exit");

				sh.waitFor();
				Log.i(GnuPrivacyGuard.TAG, "Done!");
			} catch (Exception e) {
				Log.e(GnuPrivacyGuard.TAG, "Error!!!", e);
			} finally {
				synchronized (GnuPrivacyGuard.this) {
					commandThread = null;
				}
				sendBroadcast(new Intent(COMMAND_FINISHED));
			}
		}
	}

	class LogUpdate extends StreamThread.StreamUpdate {

		StringBuffer sb = new StringBuffer();

		@Override
		public void update(String val) {
			log.append(val);
			sendBroadcast(new Intent(LOG_UPDATE));
		}
	}

	public static void writeCommand(OutputStream os, String command) throws Exception {
		os.write((command + "\n").getBytes("ASCII"));
	}

	private void registerReceivers() {
		logUpdateReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				updateLog();
			}
		};
		registerReceiver(logUpdateReceiver, new IntentFilter(GnuPrivacyGuard.LOG_UPDATE));

		commandFinishedReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
			}
		};
		registerReceiver(commandFinishedReceiver, new IntentFilter(
				GnuPrivacyGuard.COMMAND_FINISHED));
	}

	private void unregisterReceivers() {
		if (logUpdateReceiver != null)
			unregisterReceiver(logUpdateReceiver);

		if (commandFinishedReceiver != null)
			unregisterReceiver(commandFinishedReceiver);
	}

}
