package buzz.getcoco.auth;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import net.openid.appauth.AuthState;
import net.openid.appauth.AuthorizationException;
import net.openid.appauth.AuthorizationResponse;
import net.openid.appauth.RegistrationResponse;
import net.openid.appauth.TokenResponse;
import org.json.JSONException;
import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

public class AuthStateManager {

  private static final AtomicReference<WeakReference<AuthStateManager>> INSTANCE_REF =
      new AtomicReference<>(new WeakReference<>(null));

  private static final String TAG = "AuthStateManager";

  private static final String STORE_NAME = "AuthState";
  private static final String KEY_STATE = "state";

  private final SharedPreferences preferences;
  private final ReentrantLock prefsLock;
  private final AtomicReference<AuthState> currentAuthState;

  public static AuthStateManager getInstance(@NonNull Context context) {
    AuthStateManager manager = INSTANCE_REF.get().get();
    if (manager == null) {
      manager = new AuthStateManager(context.getApplicationContext());
      INSTANCE_REF.set(new WeakReference<>(manager));
    }

    return manager;
  }

  private AuthStateManager(Context context) {
    preferences = context.getSharedPreferences(STORE_NAME, Context.MODE_PRIVATE);
    prefsLock = new ReentrantLock();
    currentAuthState = new AtomicReference<>();
  }

  private void writeState(@Nullable AuthState state) {
    prefsLock.lock();
    try {
      SharedPreferences.Editor editor = preferences.edit();
      if (state == null) {
        editor.remove(KEY_STATE);
      } else {
        editor.putString(KEY_STATE, state.jsonSerializeString());
      }

      if (!editor.commit()) {
        throw new IllegalStateException("Failed to write state to shared prefs");
      }
    } finally {
      prefsLock.unlock();
    }
  }

  @NonNull
  private AuthState readState() {
    prefsLock.lock();
    try {
      String currentState = preferences.getString(KEY_STATE, null);
      if (currentState == null) {
        return new AuthState();
      }

      try {
        return AuthState.jsonDeserialize(currentState);
      } catch (JSONException ex) {
        Log.w(TAG, "Failed to deserialize stored auth state - discarding");
        return new AuthState();
      }
    } finally {
      prefsLock.unlock();
    }
  }

  @NonNull
  public AuthState getCurrent() {
    if (currentAuthState.get() != null) {
      return currentAuthState.get();
    }

    AuthState state = readState();
    if (currentAuthState.compareAndSet(null, state)) {
      return state;
    } else {
      return currentAuthState.get();
    }
  }

  @NonNull
  public AuthState replace(@NonNull AuthState state) {
    writeState(state);
    currentAuthState.set(state);
    return state;
  }

  @NonNull
  public AuthState updateAfterAuthorization(
      @Nullable AuthorizationResponse response,
      @Nullable AuthorizationException ex) {
    AuthState current = getCurrent();
    current.update(response, ex);
    return replace(current);
  }

  @NonNull
  public AuthState updateAfterTokenResponse(
      @Nullable TokenResponse response,
      @Nullable AuthorizationException ex) {
    AuthState current = getCurrent();
    current.update(response, ex);
    return replace(current);
  }

  @NonNull
  public AuthState updateAfterRegistration(
      RegistrationResponse response,
      AuthorizationException ex) {
    AuthState current = getCurrent();
    if (ex != null) {
      return current;
    }

    current.update(response);
    return replace(current);
  }
}
