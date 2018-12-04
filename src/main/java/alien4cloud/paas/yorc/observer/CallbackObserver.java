package alien4cloud.paas.yorc.observer;

import io.reactivex.Observer;
import io.reactivex.SingleObserver;
import io.reactivex.disposables.Disposable;

import java.util.function.Consumer;

public class CallbackObserver<T> implements SingleObserver<T>, Observer<T> {

    private final Consumer<T> dataConsumer;

    private final Consumer<Throwable> errorConsumer;

    public CallbackObserver(Consumer<T> dataConsumer,Consumer<Throwable> errorConsumer) {
        this.dataConsumer = dataConsumer;
        this.errorConsumer = errorConsumer;
    }

    @Override
    public void onSubscribe(Disposable disposable) {
    }

    @Override
    public void onNext(T t) {
        dataConsumer.accept(t);
    }

    @Override
    public void onSuccess(T t) {
        dataConsumer.accept(t);
    }

    @Override
    public void onError(Throwable throwable) {
        errorConsumer.accept(throwable);
    }

    @Override
    public void onComplete() {

    }
}
