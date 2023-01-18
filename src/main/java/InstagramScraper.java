import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;

import Classes.InstagramPostNode;
import Classes.InstagramPostPage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class InstagramScraper {
    private static final String ACCESS_TOKEN = "ACCESS_TOKEN";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.61 Safari/537.36";
    private static final String BASE_URL = "https://api.instagram.com/v1";
    private static final String USER_ENDPOINT = BASE_URL + "/users/%s/media/recent";
    private static final String TAG_ENDPOINT = BASE_URL + "/tags/%s/media/recent";

    public static void main(String[] args) throws IOException {
        // Create HTTP client
        OkHttpClient client = new OkHttpClient();

        // Get user's ID
        String username = "USERNAME";
        String userId = getUserId(client, username);

        // Get user's recent posts
        String endpoint = String.format(USER_ENDPOINT, userId);
        JsonNode recentMedia = getRecentMedia(client, endpoint);
        JsonNode mediaNodes = recentMedia.get("data");
        for (JsonNode mediaNode : mediaNodes) {
            // Get post's URL
            String shortcode = mediaNode.get("code").asText();
            String postUrl = "https://www.instagram.com/p/" + shortcode;

            // Download post
            downloadPost(client, postUrl, shortcode);
        }

        // Check for more pages
        JsonNode pagination = recentMedia.get("pagination");
        while (pagination != null && pagination.get("next_url") != null) {
            // Get next page of posts
            String nextUrl = pagination.get("next_url").asText();
            recentMedia = getRecentMedia(client, nextUrl);
            mediaNodes = recentMedia.get("data");
            for (JsonNode mediaNode : mediaNodes) {
                // Get post's URL
                String shortcode = mediaNode.get("code").asText();
                String postUrl = "https://www.instagram.com/p/" + shortcode;

                // Download post
                downloadPost(client, postUrl, shortcode);
            }

            // Update pagination
            pagination = recentMedia.get("pagination");
        }
    }

    private static String getUserId(OkHttpClient client, String username) throws IOException {
        // Build URL to get user's ID
        HttpUrl.Builder urlBuilder = HttpUrl.parse(BASE_URL + "/users/search").newBuilder();
        urlBuilder.addQueryParameter("q", username);
        urlBuilder.addQueryParameter("access_token", ACCESS_TOKEN);
        String url = urlBuilder.build().toString();
        // Make API request to get user's ID
        Request request = new Request.Builder()
                .url(url)
                .build();
        Response response = client.newCall(request).execute();

        // Parse response
        ObjectMapper mapper = new ObjectMapper();
        JsonNode rootNode = mapper.readTree(response.body().string());
        JsonNode dataNode = rootNode.get("data");
        JsonNode userNode = dataNode.get(0);
        return userNode.get("id").asText();
    }

    private static JsonNode getRecentMedia(OkHttpClient client, String endpoint) throws IOException {
        // Build URL to get recent media
        HttpUrl.Builder urlBuilder = HttpUrl.parse(endpoint).newBuilder();
        urlBuilder.addQueryParameter("access_token", ACCESS_TOKEN);
        String url = urlBuilder.build().toString();

        // Make API request to get recent media
        Request request = new Request.Builder()
                .url(url)
                .build();
        Response response = client.newCall(request).execute();

        // Parse response
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readTree(response.body().string());
    }

    private static void downloadPost(OkHttpClient client, String url, String shortcode) throws IOException {
        // Send request to download post page
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build();
        Response response = client.newCall(request).execute();

        // Parse response
        String html = response.body().string();
        String[] lines = html.split("\n");
        String variableLine = null;
        for (String line : lines) {
            if (line.contains("<script type=\"text/javascript\">window._sharedData")) {
                variableLine = line;
                break;
            }
        }
        if (variableLine == null) {
            return;
        }

        // Extract JSON from JavaScript variable
        String json = variableLine.split(" = ")[1];
        json = json.substring(0, json.length() - 1);

        // Parse JSON
        ObjectMapper mapper = new ObjectMapper();
        TypeReference<InstagramPostPage> typeRef = new TypeReference<InstagramPostPage>() {
        };
        InstagramPostPage postPage = mapper.readValue(json, typeRef);
        InstagramPostNode node = postPage.getGraphql().getShortcodeMedia();

        // Check if post is a slideshow or video
        if (node.isIsVideo()) {
            // Download video
            URL videoUrl = new URL(node.getVideoUrl());
            String filename = shortcode + ".mp4";
            // Save video to disk with specified filename
            try (InputStream input = videoUrl.openStream();
                 FileOutputStream output = new FileOutputStream(filename)) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = input.read(buffer)) != -1) {
                    output.write(buffer, 0, bytesRead);
                }
            }
        } else if (node.getEdgeSidecarToChildren() != null) {
            // Download slideshow
            List<InstagramPostNode> slideshowNodes = node.getEdgeSidecarToChildren().getEdges();
            for (int i = 0; i < slideshowNodes.size(); i++) {
                InstagramPostNode slideshowNode = slideshowNodes.get(i);
                if (slideshowNode.isIsVideo()) {
                    // Download video
                    URL videoUrl = new URL(slideshowNode.getVideoUrl());
                    String filename = shortcode + "_" + i + ".mp4";
                    // Save video to disk with specified filename
                    try (InputStream input = videoUrl.openStream();
                         FileOutputStream output = new FileOutputStream(filename)) {
                        byte[] buffer = new byte[1024];
                        int bytesRead;
                        while ((bytesRead = input.read(buffer)) != -1) {
                            output.write(buffer, 0, bytesRead);
                        }
                    }
                } else {
                    // Download image
                    URL imageUrl = new URL(slideshowNode.getDisplayUrl());
                    String filename = shortcode + "_" + i + ".jpg";
                    // Save image to disk with specified filename
                    try (InputStream input = imageUrl.openStream();
                         FileOutputStream output = new FileOutputStream(filename)) {
                        byte[] buffer = new byte[1024];
                        int bytesRead;
                        while ((bytesRead = input.read(buffer)) != -1) {
                            output.write(buffer, 0, bytesRead);
                        }
                    }
                }
            }
        }
    }
}
//This program uses the OkHttp library to make HTTP requests to the Instagram API. It first gets the user's ID by making a request to the `/users/search` endpoint, then gets the user's recent posts by making a request to the `/users/{user-id}/media/recent` endpoint. It then iterates through the list of posts and downloads each one, checking if it is a slideshow or video and downloading it accordingly. It also handles pagination by making additional requests to the `/users/{user-id}/media/recent` endpoint with the `next_url` parameter as specified in the `pagination` field of the response.