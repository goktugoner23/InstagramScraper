package Classes;

public class InstagramPostNode {
    private boolean isVideo;
    private String videoUrl;
    private InstagramPostEdge edgeSidecarToChildren;
    private String displayUrl;

    public boolean isIsVideo() {
        return isVideo;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public InstagramPostEdge getEdgeSidecarToChildren() {
        return edgeSidecarToChildren;
    }

    public String getDisplayUrl() {
        return displayUrl;
    }
}
