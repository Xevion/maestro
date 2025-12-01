package maestro.api.event.events;

public final class SprintStateEvent {

    private Boolean state;

    public void setState(boolean state) {
        this.state = state;
    }

    public Boolean getState() {
        return this.state;
    }
}
