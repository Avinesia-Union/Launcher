package pro.gravit.launchserver.auth.texture;

import pro.gravit.launcher.HTTPRequest;
import pro.gravit.launcher.Launcher;
import pro.gravit.launcher.profiles.Texture;

import java.io.IOException;
import java.net.URL;
import java.util.UUID;

public class JsonTextureProvider extends TextureProvider {
    public String url;
	
    @Override
    public void close() throws IOException {
        //None
    }

    @Override
    public Texture getCloakTexture(UUID uuid, String username, String client) throws IOException {
        return getTextures(uuid, username, client).cloak;
    }

    @Override
    public Texture getSkinTexture(UUID uuid, String username, String client) throws IOException {
        return getTextures(uuid, username, client).skin;
    }

    @Override
    public SkinAndCloakTextures getTextures(UUID uuid, String username, String client) {
        try {
            var result = HTTPRequest.jsonRequest(null, "GET", new URL(RequestTextureProvider.getTextureURL(url, uuid, username, client)));
            return Launcher.gsonManager.gson.fromJson(result, SkinAndCloakTextures.class);
        } catch (IOException e) {
            return new SkinAndCloakTextures(null, null);
        }
    }
}