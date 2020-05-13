package alien4cloud.paas.yorc.exception;

public class YorcDeploymentException extends Exception {

    private static final long serialVersionUID = 1681817657961492822L;

    public YorcDeploymentException(String message) {
        super(message);
    }

    public YorcDeploymentException(String message, Throwable cause) {
        super(message, cause);
    }

    public YorcDeploymentException(Throwable cause) {
        super(cause);
    }

    public YorcDeploymentException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
