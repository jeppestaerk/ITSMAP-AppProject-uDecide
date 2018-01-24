package dk.au.ase.itsmap.e17.appproject.gruppe7.udecide.activitys;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Toast;

import com.facebook.AccessToken;
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.login.LoginResult;
import com.facebook.login.widget.LoginButton;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FacebookAuthProvider;
import com.google.firebase.auth.FirebaseAuth;

import dk.au.ase.itsmap.e17.appproject.gruppe7.udecide.R;

// https://firebase.google.com/docs/auth/android/facebook-login?authuser=0
public class SignInActivity extends AppCompatActivity {

    private static final String TAG = "SignInActivity";
    private String[] permissions = new String[]{"read_custom_friendlists", "public_profile", "user_friends", "email"};

    private FirebaseAuth auth;
    private CallbackManager callbackManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_in);

        auth = FirebaseAuth.getInstance();
        callbackManager = CallbackManager.Factory.create();
        LoginButton loginButton = findViewById(R.id.login_button);

        loginButton.setReadPermissions(permissions);
        loginButton.registerCallback(callbackManager, facebookCallback());
    }


    @Override
    protected void onStart() {
        super.onStart();

        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            Log.d(TAG, "onStart:user:true");
            finish();
        } else {
            Log.d(TAG, "onStart:user:false");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "onActivityResult:requestCode: " + requestCode);
        Log.d(TAG, "onActivityResult:resultCode: " + resultCode);
        Log.d(TAG, "onActivityResult:data: " + data);

        callbackManager.onActivityResult(requestCode, resultCode, data);
    }

    private FacebookCallback<LoginResult> facebookCallback() {

        return new FacebookCallback<LoginResult>() {
            @Override
            public void onSuccess(LoginResult loginResult) {
                Log.d(TAG, "facebook:onSuccess:" + loginResult);
                handleFacebookAccessToken(loginResult.getAccessToken());
            }

            @Override
            public void onCancel() {
                Log.d(TAG, "facebook:onCancel");
                Toast.makeText(SignInActivity.this, R.string.FacebookSignInAuthenticationCanceled, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(FacebookException error) {
                Log.d(TAG, "facebook:onError", error);
                Toast.makeText(SignInActivity.this, R.string.FacebookSignInAuthenticationError, Toast.LENGTH_SHORT).show();
            }
        };
    }

    private void handleFacebookAccessToken(AccessToken token) {
        Log.d(TAG, "handleFacebookAccessToken: " + token);

        AuthCredential credential = FacebookAuthProvider.getCredential(token.getToken());
        auth.signInWithCredential(credential).addOnCompleteListener(this, signInWithCredentialOnCompleteListener());
    }

    private OnCompleteListener<AuthResult> signInWithCredentialOnCompleteListener() {

        return new OnCompleteListener<AuthResult>() {
            @Override
            public void onComplete(@NonNull Task<AuthResult> task) {
                if (task.isSuccessful()) {
                    Log.d(TAG, "signInWithCredential:success");
                    finish();
                } else {
                    Log.w(TAG, "signInWithCredential:failure", task.getException());
                    Toast.makeText(SignInActivity.this, R.string.FirebaseSignInAuthenticationFailed, Toast.LENGTH_SHORT).show();
                }
            }
        };
    }
}
