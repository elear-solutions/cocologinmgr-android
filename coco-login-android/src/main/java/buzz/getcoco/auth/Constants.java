package buzz.getcoco.auth;

public class Constants {
  // Constants related to creating an auth request
  // These keys which are mandatory to be specified for login activity to start
  public static final String AUTH_ENDPOINT = "auth_endpoint";
  public static final String TOKEN_ENDPOINT = "token_endpoint";
  public static final String SCOPE = "scope";

  // Constants related to response received from the SDK
  // Keys present in data intent received
  public static final String KEY_AUTH_STATE = "auth_state";   // contains all tokens
  public static final String KEY_FAILURE = "failure_message"; // can be used as error message

  // Constants related to authorization status packed in response intent
  public static final String CANCELLED = "cancelled";
  public static final String REAUTHORIZE = "reauthorize";
  public static final String CODE_EXCHANGE_FAILED = "code_exchange_failed";
}
