/***************************************************************************************
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 * *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 * *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ankipro.anki;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ankipro.model.Produto;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.x3wiser.anim.ActivityTransitionAnimation;
import com.x3wiser.anki.AnkiActivity;
import com.x3wiser.anki.AnkiProApp;
import com.x3wiser.anki.R;
import com.x3wiser.anki.UIUtils;
import com.x3wiser.async.Connection;
import com.x3wiser.themes.StyledProgressDialog;

import java.lang.reflect.Type;
import java.util.List;

import timber.log.Timber;

public class AnkiProAccount extends AnkiActivity {
    private final static int STATE_LOG_IN = 1;
    private final static int STATE_LOGGED_IN = 2;

    private View mLoginToAnkiProAccountView;
    private View mLoggedIntoAnkiProAccountView;

    private EditText mUsername;
    private EditText mPassword;

    private TextView mUsernameLoggedIn;

    private MaterialDialog mProgressDialog;
    Toolbar mToolbar = null;


    private void switchToState(int newState) {
        switch (newState) {
            case STATE_LOGGED_IN:
                String username = AnkiProApp.getSharedPrefs(getBaseContext()).getString("username", "");
                mUsernameLoggedIn.setText(username);
                mToolbar = (Toolbar) mLoggedIntoAnkiProAccountView.findViewById(R.id.toolbar);
                if (mToolbar != null) {
                    mToolbar.setTitle(getString(R.string.ankipro_sync_account));  // This can be cleaned up if all three main layouts are guaranteed to share the same toolbar object
                    setSupportActionBar(mToolbar);
                }
                setContentView(mLoggedIntoAnkiProAccountView);
                break;

            case STATE_LOG_IN:
                mToolbar = (Toolbar) mLoginToAnkiProAccountView.findViewById(R.id.toolbar);
                if (mToolbar != null) {
                    mToolbar.setTitle(getString(R.string.ankipro_sync_account));  // This can be cleaned up if all three main layouts are guaranteed to share the same toolbar object
                    setSupportActionBar(mToolbar);
                }
                setContentView(mLoginToAnkiProAccountView);
                break;
        }


        supportInvalidateOptionsMenu();  // Needed?
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mayOpenUrl(Uri.parse(getResources().getString(R.string.register_url)));
        initAllContentViews();

        SharedPreferences preferences = AnkiProApp.getSharedPrefs(getBaseContext());
        if (preferences.getString("hkey", "").length() > 0) {
            switchToState(STATE_LOGGED_IN);
        } else {
            switchToState(STATE_LOG_IN);
        }
    }


    // Commented awaiting the resolution of the next issue: http://code.google.com/p/anki/issues/detail?id=1932
    // private boolean isUsernameAndPasswordValid(String username, String password) {
    // return isLoginFieldValid(username) && isLoginFieldValid(password);
    // }
    //
    //
    // private boolean isLoginFieldValid(String loginField) {
    // boolean loginFieldValid = false;
    //
    // if (loginField.length() >= 2 && loginField.matches("[A-Za-z0-9]+")) {
    // loginFieldValid = true;
    // }
    //
    // return loginFieldValid;
    // }

    private void saveAnkiProUserInformation(String username, String ninjakey,String password, List<Produto> result) {
        SharedPreferences preferences = AnkiProApp.getSharedPrefs(getBaseContext());
        Editor editor = preferences.edit();
        editor.remove("username");
        editor.remove("hkey");
        editor.remove("ninjaproducts");
        editor.remove("password");

        //After cleaning the data
        editor.putString("username", username);
        editor.putString("hkey", ninjakey);
        editor.putString("password", password);

        Gson gson = new Gson();
        Type listOfTestObject = new TypeToken<List<Produto>>(){}.getType();
        String sproducts = gson.toJson(result, listOfTestObject);
        editor.putString("ninjaproducts",sproducts);

        editor.commit();
    }


    private void login() {
        // Hide soft keyboard
        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(mUsername.getWindowToken(), 0);

        String username = mUsername.getText().toString().trim(); // trim spaces, issue 1586
        String password = mPassword.getText().toString();

        /*
         * Commented awaiting the resolution of the next issue: http://code.google.com/p/anki/issues/detail?id=1932
         * if(isUsernameAndPasswordValid(username, password)) { Connection.login(loginListener, new
         * Connection.Payload(new Object[] {username, password})); } else { mInvalidUserPassAlert.show(); }
         */

        if (!"".equalsIgnoreCase(username) && !"".equalsIgnoreCase(password)) {
            Connection.login(loginListener, new Connection.Payload(new Object[]{username, password}));
        } else {
            UIUtils.showSimpleSnackbar(this, R.string.invalid_username_password, true);
        }
    }


    private void logout() {
        SharedPreferences preferences = AnkiProApp.getSharedPrefs(getBaseContext());
        Editor editor = preferences.edit();
        editor.remove("username");
        editor.remove("hkey");
        editor.remove("ninjaproducts");

        editor.commit();
        //  force media resync on deauth
        try {
            getCol().getMedia().forceResync();
        } catch (SQLiteException e) {
            Timber.e("AnkiProAccount -.logout()  reinitializing media db due to sqlite error");
            getCol().getMedia()._initDB();
        }
        switchToState(STATE_LOG_IN);
    }


    private void resetPassword() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(getResources().getString(R.string.resetankipro_pw_url)));
        startActivity(intent);
    }


    private void initAllContentViews() {
        mLoginToAnkiProAccountView = getLayoutInflater().inflate(R.layout.ankipro_account, null);
        mUsername = (EditText) mLoginToAnkiProAccountView.findViewById(R.id.username);
        mPassword = (EditText) mLoginToAnkiProAccountView.findViewById(R.id.password);

        Button loginButton = (Button) mLoginToAnkiProAccountView.findViewById(R.id.login_button);
        loginButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                login();
            }

        });

        Button resetPWButton = (Button) mLoginToAnkiProAccountView.findViewById(R.id.reset_ankipro_password_button);
        resetPWButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                resetPassword();
            }
        });

        Button signUpButton = (Button) mLoginToAnkiProAccountView.findViewById(R.id.sign_up_button);
        signUpButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                openUrl(Uri.parse(getResources().getString(R.string.link_ankipro_register)));
            }

        });

        mLoggedIntoAnkiProAccountView = getLayoutInflater().inflate(R.layout.ankipro_account_logged_in, null);
        mUsernameLoggedIn = (TextView) mLoggedIntoAnkiProAccountView.findViewById(R.id.username_logged_in);
        Button logoutButton = (Button) mLoggedIntoAnkiProAccountView.findViewById(R.id.logout_button);
        logoutButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                logout();
            }

        });
    }


    /**
     * Listeners
     */
    Connection.TaskListener loginListener = new Connection.TaskListener() {

        @Override
        public void onProgressUpdate(Object... values) {
            // Pass
            System.out.println("progrss");
        }


        @Override
        public void onPreExecute() {
            Timber.d("AnkiProAccount - loginListener.onPreExcecute()");
            if (mProgressDialog == null || !mProgressDialog.isShowing()) {
                mProgressDialog = StyledProgressDialog.show(AnkiProAccount.this, "",
                        getResources().getString(R.string.alert_logging_message), false);
            }
        }


        @Override
        public void onPostExecute(Connection.Payload data) {
            if (mProgressDialog != null) {
                mProgressDialog.dismiss();
            }

            if (data.success) {
                Timber.i("AnkiProAccount - User successfully logged in!");
                saveAnkiProUserInformation((String) data.data[0], (String) data.data[1],(String)data.data[2],(List<Produto>)data.result);

                Intent i = AnkiProAccount.this.getIntent();
                if (i.hasExtra("notLoggedIn") && i.getExtras().getBoolean("notLoggedIn", false)) {
                    AnkiProAccount.this.setResult(RESULT_OK, i);
                    finishWithAnimation(ActivityTransitionAnimation.FADE);
                } else {
                    // Show logged view
                    mUsernameLoggedIn.setText((String) data.data[0]);
                    switchToState(STATE_LOGGED_IN);
                }
            } else {
                Timber.e("AnkiProAccount - Login failed, error code %d", data.returnType);
                if (data.returnType == 401) {
                    // If the deck is empty and has no children then show a message saying it's empty
                    final Uri helpUrl = Uri.parse(getResources().getString(R.string.link_ankipro_register));
                    mayOpenUrl(helpUrl);
                    UIUtils.showSnackbar(AnkiProAccount.this, R.string.wrong_ankipro_password, false, R.string.wrong_ankipro_help, new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            openUrl(helpUrl);
                        }
                    }, findViewById(R.id.root_layout));
                }else if (data.returnType == 403) {
                    //TODO make AnkiNinja return no authorized
                    UIUtils.showSimpleSnackbar(AnkiProAccount.this, R.string.invalid_ankipro_username_password, true);
                } else {
                    UIUtils.showSimpleSnackbar(AnkiProAccount.this, R.string.connection_error_message, true);
                }
            }
        }


        @Override
        public void onDisconnected() {
            UIUtils.showSimpleSnackbar(AnkiProAccount.this, R.string.youre_offline, true);
        }
    };


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            Timber.i("AnkiProAccount - onBackPressed()");
            finishWithAnimation(ActivityTransitionAnimation.FADE);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mProgressDialog!=null && mProgressDialog.isShowing()){
            mProgressDialog.dismiss();
        } }
}
