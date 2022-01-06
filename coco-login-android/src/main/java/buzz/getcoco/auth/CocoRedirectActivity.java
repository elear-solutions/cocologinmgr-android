package buzz.getcoco.auth;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import net.openid.appauth.AuthorizationManagementActivity;

public class CocoRedirectActivity extends AppCompatActivity {
  /**
   * copied from {@link net.openid.appauth.RedirectUriReceiverActivity}
   */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    // while this does not appear to be achieving much, handling the redirect in this way
    // ensures that we can remove the browser tab from the back stack. See the documentation
    // on AuthorizationManagementActivity for more details.
    startActivity(AuthorizationManagementActivity.createResponseHandlingIntent(
        this, getIntent().getData()));
    finish();
  }
}
