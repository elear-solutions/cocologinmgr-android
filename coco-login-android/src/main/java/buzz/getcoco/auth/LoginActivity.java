package buzz.getcoco.auth;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabsIntent;
import com.google.gson.JsonObject;
import net.openid.appauth.AppAuthConfiguration;
import net.openid.appauth.AuthState;
import net.openid.appauth.AuthorizationException;
import net.openid.appauth.AuthorizationRequest;
import net.openid.appauth.AuthorizationResponse;
import net.openid.appauth.AuthorizationService;
import net.openid.appauth.AuthorizationServiceConfiguration;
import net.openid.appauth.ResponseTypeValues;
import net.openid.appauth.TokenResponse;
import java.util.Objects;

public class LoginActivity extends AppCompatActivity {

  private static final String TAG = "LoginActivity";
  private static final String KEY_AUTH_STARTED = "buzz.getcoco.auth.authStarted";

  private AuthorizationRequest authRequest;
  private CustomTabsIntent authIntent;

  private AuthorizationService authService;
  private AuthState authState;

  private boolean authStarted;
  private String authEndpoint;
  private String tokenEndpoint;
  private String scope;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    if (null != savedInstanceState) {
      this.authStarted = savedInstanceState.getBoolean(KEY_AUTH_STARTED);
    }

    if (!authStarted) {
      Intent intent = getIntent();

      if (intent.hasExtra(Constants.AUTH_ENDPOINT)
          && intent.hasExtra(Constants.TOKEN_ENDPOINT)
          && intent.hasExtra(Constants.SCOPE)) {

        authEndpoint = intent.getStringExtra(Constants.AUTH_ENDPOINT);
        tokenEndpoint = intent.getStringExtra(Constants.TOKEN_ENDPOINT);
        scope = intent.getStringExtra(Constants.SCOPE);
      }

      String clientId;
      try {
        ApplicationInfo appInfo = this.getPackageManager()
            .getApplicationInfo(this.getPackageName(), PackageManager.GET_META_DATA);

        Bundle metadata = Objects.requireNonNull(appInfo.metaData);
        clientId = metadata.getString("buzz.getcoco.auth.client_id");

        Objects.requireNonNull(clientId, "buzz.getcoco.auth.client_id missing from manifest");
      } catch (PackageManager.NameNotFoundException | NullPointerException e) {
        Log.w(TAG, "onCreate: failed to load metadata: ", e);
        throw new IllegalArgumentException("initialize client_id in app manifest", e);
      }

      Log.d(TAG, "onCreate: authEndpoint: " + authEndpoint
          + ", tokenEndpoint: " + tokenEndpoint
          + ", scope: " + scope
          + ", clientId: " + clientId);


      // initializes authService, authRequest and authIntent
      initializeAppAuth(authEndpoint, tokenEndpoint, clientId, scope);
    } else {
      processAuthResponse();
    }
  }

  @Override
  protected void onStart() {
    super.onStart();

    if (authState.isAuthorized()) {
      Log.d(TAG, "onStart: accessToken: " + authState.getAccessToken());
      setResult(RESULT_OK, getSuccessIntent());
      finish();
    }

    if (!authStarted) {
      authorize();
      authStarted = true;
    }
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    if (null != authService) {
      authService.dispose();
     }
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    Log.d(TAG, "onNewIntent: data: " + intent.getExtras());
    setIntent(intent);
    processAuthResponse();
  }

  @Override
  protected void onSaveInstanceState(@NonNull Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putBoolean(KEY_AUTH_STARTED, authStarted);
  }

  private void authorize() {
    Intent postAuthorizationIntent = new Intent(this, LoginActivity.class);
    postAuthorizationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

    PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, postAuthorizationIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);

    Log.d(TAG, "authorize: performing authorization with pending intent: " + pendingIntent);
    authService.performAuthorizationRequest(authRequest, pendingIntent, pendingIntent, authIntent);
  }

  private void initializeAppAuth(String authEndpoint, String tokenEndpoint,
                                 String clientId, String scope) {
    Log.d(TAG, "initializeAppAuth: Initializing");
    recreateAuthService();

    Objects.requireNonNull(authEndpoint, "Provide Auth Endpoint");
    Objects.requireNonNull(tokenEndpoint, "Provide Token Endpoint");
    Objects.requireNonNull(scope, "Provide Scope");
    Objects.requireNonNull(clientId, "Provide Client ID");

    AuthorizationServiceConfiguration config = new AuthorizationServiceConfiguration(
        Uri.parse(authEndpoint),
        Uri.parse(tokenEndpoint),
        null,
        null);

    this.authState = new AuthState(config);

    // initialize auth request
    Log.d(TAG, "createAuthRequest: creating auth request using clientId: " + clientId);

    AuthorizationRequest.Builder authRequestBuilder =
        new AuthorizationRequest.Builder(
            authState.getAuthorizationServiceConfiguration(),
            clientId,
            ResponseTypeValues.CODE,
            null)
            .setScope(scope);

    this.authRequest = authRequestBuilder.build();

    // warmup browser instance
    Log.d(TAG, "warmUpBrowser: making browser instance for auth request");

    CustomTabsIntent.Builder intentBuilder =
        authService.createCustomTabsIntentBuilder(authRequest.toUri());
    this.authIntent = intentBuilder.build();
  }

  private void recreateAuthService() {
    if (null != authService) {
      Log.d(TAG, "recreateAuthService: disposing auth service");
      authService.dispose();
    }

    authService = new AuthorizationService(this, new AppAuthConfiguration.Builder().build());
    this.authRequest = null;
    this.authIntent = null;
  }

  private void processAuthResponse() {
    AuthorizationResponse response = AuthorizationResponse.fromIntent(getIntent());
    AuthorizationException ex = AuthorizationException.fromIntent(getIntent());

    Log.d(TAG, "processAuthResponse: response: " + response + ", exception:" + ex);
    if (response != null || ex != null) {
      authState.update(response, ex);
    }

    if (response != null && response.authorizationCode != null) {
      // authorization code exchange is required
      authState.update(response, ex);
      exchangeAuthorizationCode(response);
    } else if (ex != null) {
      Log.d(TAG, "processAuthResponse: Authorization flow failed: exception: " + ex.getMessage());
      setResult(RESULT_CANCELED, getFailureIntent("Authorization flow failed: " + ex.getMessage()));
      finish();
    } else {
      Log.d(TAG, "processAuthResponse: No authorization state retained - reauthorization required");
      setResult(RESULT_CANCELED, getFailureIntent("No authorization state retained - reauthorization required"));
      finish();
    }
  }

  private void exchangeAuthorizationCode(@NonNull AuthorizationResponse response) {
    Log.d(TAG, "exchangeAuthorizationCode: started");
    authService.performTokenRequest(response.createTokenExchangeRequest(), this::handleCodeExchangeResponse);
  }

  private void handleCodeExchangeResponse(TokenResponse tokenResponse, AuthorizationException authException) {
    Log.d(TAG, "handleCodeExchangeResponse: started with tokenResponse: " + tokenResponse + ", Exception: " + authException);

    authState.update(tokenResponse, authException);
    if (!authState.isAuthorized()) {
      Log.d(TAG, "handleCodeExchangeResponse: " + Constants.CODE_EXCHANGE_FAILED);
      setResult(RESULT_CANCELED, getFailureIntent(Constants.CODE_EXCHANGE_FAILED));
    } else {
      Log.d(TAG, "handleCodeExchangeResponse: authState: " + authState.jsonSerializeString());
      setResult(RESULT_OK, getSuccessIntent());
    }
    finish();
  }

  private Intent getFailureIntent(@NonNull String message) {
    return new Intent()
        .putExtra(Constants.KEY_FAILURE, message);
  }

  private Intent getSuccessIntent() {
    JsonObject jo = new JsonObject();
    jo.addProperty("access_token", authState.getAccessToken());
    jo.addProperty("refresh_token", authState.getRefreshToken());

    if (null != authState.getLastTokenResponse()) {

      Long expiresAt = authState.getLastTokenResponse().accessTokenExpirationTime;
      long expiresIn = ((null == expiresAt) ? 0 : expiresAt) - System.currentTimeMillis();

      expiresIn = Math.abs(expiresIn);

      jo.addProperty("token_type", authState.getLastTokenResponse().tokenType);
      jo.addProperty("expires_in", expiresIn);
    }

    return new Intent()
        .putExtra(Constants.KEY_AUTH_STATE, jo.toString());
  }
}
