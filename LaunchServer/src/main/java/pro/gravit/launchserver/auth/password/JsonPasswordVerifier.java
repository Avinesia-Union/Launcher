package pro.gravit.launchserver.auth.password;

import pro.gravit.launchserver.auth.core.JsonCoreProvider;

import java.net.http.HttpClient;

public class JsonPasswordVerifier extends PasswordVerifier {
    private transient final HttpClient client = HttpClient.newBuilder().build();
    public String url;
    public String bearerToken;

    @Override
    public boolean check(String encryptedPassword, String password) {
        JsonPasswordResponse response = JsonCoreProvider.jsonRequest(new JsonPasswordRequest(encryptedPassword, password), url, bearerToken, JsonPasswordResponse.class, client);
        if (response != null) {
            return response.success;
        }
        return false;
    }

    public static class JsonPasswordRequest {
        public String encryptedPassword;
        public String password;

        public JsonPasswordRequest(String encryptedPassword, String password) {
            this.encryptedPassword = encryptedPassword;
            this.password = password;
        }
    }

    public static class JsonPasswordResponse {
        public boolean success;
    }
}
