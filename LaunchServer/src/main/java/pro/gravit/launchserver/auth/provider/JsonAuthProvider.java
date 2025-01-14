package pro.gravit.launchserver.auth.provider;

import com.google.gson.JsonElement;
import pro.gravit.launcher.ClientPermissions;
import pro.gravit.launcher.HTTPRequest;
import pro.gravit.launcher.Launcher;
import pro.gravit.launcher.request.auth.AuthRequest;
import pro.gravit.launcher.request.auth.password.Auth2FAPassword;
import pro.gravit.launcher.request.auth.password.AuthPlainPassword;
import pro.gravit.launcher.request.auth.password.AuthTOTPPassword;
import pro.gravit.launchserver.auth.AuthException;
import pro.gravit.utils.helper.SecurityHelper;

import java.io.IOException;
import java.net.URL;
import java.util.Objects;

public final class JsonAuthProvider extends AuthProvider {
    public URL url;
    public boolean enable2FA;
    public String apiKey;

    @Override
    public AuthProviderResult auth(String login, AuthRequest.AuthPasswordInterface password, String ip) throws IOException {
        String firstPassword;
        String secondPassword;
        if (!enable2FA) {
            if (!(password instanceof AuthPlainPassword)) throw new AuthException("This password type not supported");
            firstPassword = ((AuthPlainPassword) password).password;
            secondPassword = null;
        } else {
            if (password instanceof AuthPlainPassword) {
                firstPassword = ((AuthPlainPassword) password).password;
                secondPassword = null;
            } else if (password instanceof Auth2FAPassword) {
                if (!(((Auth2FAPassword) password).firstPassword instanceof AuthPlainPassword))
                    throw new AuthException("This password type not supported");
                firstPassword = ((AuthPlainPassword) ((Auth2FAPassword) password).firstPassword).password;
                if (!(((Auth2FAPassword) password).secondPassword instanceof AuthTOTPPassword))
                    throw new AuthException("This password type not supported");
                secondPassword = ((AuthTOTPPassword) ((Auth2FAPassword) password).secondPassword).totp;
            } else {
                throw new AuthException("This password type not supported");
            }
        }

        JsonElement content = HTTPRequest.jsonRequest(Launcher.gsonManager.gson.toJsonTree(new authRequest(login, firstPassword, secondPassword, ip, apiKey)), url);
        if (!content.isJsonObject())
            return authError("Authentication server response is malformed");
        authResult result = Launcher.gsonManager.gson.fromJson(content, authResult.class);
        if (result.username != null)
            return new AuthProviderResult(result.username, SecurityHelper.randomStringToken(), new ClientPermissions(result.permissions, result.flags));
        else return authError(Objects.requireNonNullElse(result.error, "Authentication server response is malformed"));
    }

    @Override
    public void close() {
        // pass
    }

    public static class authResult {
        String username;
        String error;
        long permissions;
        long flags;
    }

    public static class authRequest {
        final String username;
        final String password;
        final String secondPassword;
        final String ip;
        String apiKey;

        public authRequest(String username, String password, String ip) {
            this.username = username;
            this.password = password;
            this.secondPassword = null;
            this.ip = ip;
        }

        public authRequest(String username, String password, String secondPassword, String ip, String apiKey) {
            this.username = username;
            this.password = password;
            this.secondPassword = secondPassword;
            this.ip = ip;
            this.apiKey = apiKey;
        }
    }
}
