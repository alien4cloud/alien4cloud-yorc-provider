package alien4cloud.paas.yorc.exception;

public class YorcInvalidStateException  extends Exception {

    private static final long serialVersionUID = 3990987619056656350L;

    public YorcInvalidStateException() {
    }

    public YorcInvalidStateException(String message) {
        super(message);
    }

    public YorcInvalidStateException(String message, Throwable cause) {
        super(message, cause);
    }

    public YorcInvalidStateException(Throwable cause) {
        super(cause);
    }
}
