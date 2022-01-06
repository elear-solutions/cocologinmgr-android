package buzz.getcoco.auth.sample;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import buzz.getcoco.auth.Constants;
import buzz.getcoco.auth.LoginActivity;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {

  private static final String TAG = "MainActivity";

  private final ActivityResultLauncher<Intent> resultLauncher =
      registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        Intent dataIntent = result.getData();

        Objects.requireNonNull(dataIntent);

        Log.d(TAG, "data: " + dataIntent);

        if (RESULT_OK != result.getResultCode()) {
          Toast
              .makeText(this, dataIntent.getStringExtra(Constants.KEY_FAILURE), Toast.LENGTH_SHORT)
              .show();
          return;
        }

        if (dataIntent.hasExtra(Constants.KEY_AUTH_STATE)) {
          Toast
              .makeText(this, dataIntent.getStringExtra(Constants.KEY_AUTH_STATE), Toast.LENGTH_LONG)
              .show();
          return;
        }

        Log.d(TAG, "illegal state");
      });

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.activity_main);

    Button button = findViewById(R.id.btn_authorize);

    Objects.requireNonNull(button);

    button.setOnClickListener(v -> {
      Intent intent = LoginActivity.createLoginIntent(this,
          "https://api.dev.getcoco.buzz/oauth/authorize",
          "https://api.dev.getcoco.buzz/oauth/token",
          "network.mgmt");

      resultLauncher.launch(intent);
    });
  }
}
