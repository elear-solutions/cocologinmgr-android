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
import java.util.concurrent.atomic.AtomicReference;

public class LoginActivity extends AppCompatActivity {

  private static final String TAG = "LoginActivity";

  private final AtomicReference<AuthorizationRequest> authRequest = new AtomicReference<>();
  private final AtomicReference<CustomTabsIntent> authIntent = new AtomicReference<>();

  private AuthorizationService authService;
  private AuthStateManager authStateManager;

  private boolean authStarted;
  private String authEndpoint;
  private String tokenEndpoint;
  private String scope;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    String clientId;

    if (!authStarted) {
      Intent intent = getIntent();

      if (intent.hasExtra(Constants.AUTH_ENDPOINT)
          && intent.hasExtra(Constants.TOKEN_ENDPOINT)
          && intent.hasExtra(Constants.SCOPE)) {

        authEndpoint = intent.getStringExtra(Constants.AUTH_ENDPOINT);
        tokenEndpoint = intent.getStringExtra(Constants.TOKEN_ENDPOINT);
        scope = intent.getStringExtra(Constants.SCOPE);
      }

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
    } else {
      processAuthResponse();

      // should remove ?
      return;
    }

    authStateManager = AuthStateManager.getInstance(this);

    // initializes authService, authRequest and authIntent
    initializeAppAuth(authEndpoint, tokenEndpoint, clientId, scope);
  }

  @Override
  protected void onStart() {
    super.onStart();

    if (authStateManager.getCurrent().isAuthorized()) {
      Log.d(TAG, "onStart: accessToken: " + authStateManager.getCurrent().getAccessToken());
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

  private void authorize() {
    Intent postAuthorizationIntent = new Intent(this, LoginActivity.class);
    postAuthorizationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

    PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, postAuthorizationIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);

    Log.d(TAG, "authorize: performing authorization with pending intent: " + pendingIntent);
    authService.performAuthorizationRequest(authRequest.get(), pendingIntent, pendingIntent, authIntent.get());
  }

  private void initializeAppAuth(String authEndpoint, String tokenEndpoint,
                                 String clientId, String scope) {
    Log.d(TAG, "initializeAppAuth: Initializing");
    recreateAuthService();

    /*AuthorizationServiceConfiguration config = new AuthorizationServiceConfiguration(
        Uri.parse(authEndPoint),
        Uri.parse(tokenEndPoint),
        null,
        null); */

    Objects.requireNonNull(authEndpoint, "Provide Auth Endpoint");
    Objects.requireNonNull(tokenEndpoint, "Provide Token Endpoint");
    Objects.requireNonNull(scope, "Provide Scope");
    Objects.requireNonNull(clientId, "Provide Client ID");

    AuthorizationServiceConfiguration config = new AuthorizationServiceConfiguration(
        Uri.parse(authEndpoint),
        Uri.parse(tokenEndpoint),
        null,
        null);

    authStateManager.replace(new AuthState(config));
    initializeAuthRequest(clientId, scope);
    warmUpBrowser();
  }

  private void recreateAuthService() {
    if (null != authService) {
      Log.d(TAG, "recreateAuthService: disposing auth service");
      authService.dispose();
    }

    authService = createAuthService();
    authRequest.set(null);
    authIntent.set(null);
  }

  private AuthorizationService createAuthService() {
    Log.d(TAG, "createAuthService: creating Auth service");
    AppAuthConfiguration.Builder builder = new AppAuthConfiguration.Builder();

    return new AuthorizationService(this, builder.build());
  }

  private void initializeAuthRequest(String clientId, String scope) {
    Objects.requireNonNull(clientId);

    Log.d(TAG, "createAuthRequest: creating auth request using clientId: " + clientId);

    AuthorizationRequest.Builder authRequestBuilder =
        new AuthorizationRequest.Builder(
            authStateManager.getCurrent().getAuthorizationServiceConfiguration(),
            clientId,
            ResponseTypeValues.CODE,
            null)
            .setScope(scope);

    authRequest.set(authRequestBuilder.build());
  }

  private void warmUpBrowser() {
    Log.d(TAG, "warmUpBrowser: making browser instance for auth request");
    CustomTabsIntent.Builder intentBuilder =
        authService.createCustomTabsIntentBuilder(authRequest.get().toUri());
    authIntent.set(intentBuilder.build());
  }

  private void processAuthResponse() {
    AuthorizationResponse response = AuthorizationResponse.fromIntent(getIntent());
    AuthorizationException ex = AuthorizationException.fromIntent(getIntent());

    Log.d(TAG, "processAuthResponse: response: " + response + ", exception:" + ex);
    if (response != null || ex != null) {
      authStateManager.updateAfterAuthorization(response, ex);
    }

    if (response != null && response.authorizationCode != null) {
      // authorization code exchange is required
      authStateManager.updateAfterAuthorization(response, ex);
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

    authStateManager.updateAfterTokenResponse(tokenResponse, authException);
    if (!authStateManager.getCurrent().isAuthorized()) {
      Log.d(TAG, "handleCodeExchangeResponse: " + Constants.CODE_EXCHANGE_FAILED);
      setResult(RESULT_CANCELED, getFailureIntent(Constants.CODE_EXCHANGE_FAILED));
    } else {
      Log.d(TAG, "handleCodeExchangeResponse: authState: " + authStateManager.getCurrent().jsonSerializeString());
      setResult(RESULT_OK, getSuccessIntent());
    }
    finish();
  }

  private Intent getFailureIntent(String message) {
    return new Intent()
        .putExtra(Constants.KEY_AUTH_STATE, (String) null)
        .putExtra(Constants.KEY_FAILURE, message);
  }

  private Intent getSuccessIntent() {
    return new Intent()
        .putExtra(Constants.KEY_AUTH_STATE, authStateManager.getCurrent().jsonSerializeString())
        .putExtra(Constants.KEY_FAILURE, (String) null);
  }
}
