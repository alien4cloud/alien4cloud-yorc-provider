package alien4cloud.paas.yorc.context.rest.browser;

import alien4cloud.paas.yorc.context.rest.response.Link;
import io.reactivex.Observable;
import io.reactivex.functions.Function;
import lombok.ToString;

import java.util.Arrays;

public class Browser {

    @ToString
    public static class Context {

        public final Object[] context;

        Context(Object t) {
            this.context = new Object[] { t };
        }

        Context(Context parent, Object t) {
            this.context = Arrays.copyOf(parent.context,parent.context.length + 1);
            this.context[this.context.length - 1] = t;
        }

        public Object last() {
            return this.context[this.context.length - 1];
        }

        public Object get(int index) {
            return context[index];
        }

        public <T> Observable<Context> follow(String rel, Function<String,Observable<T>> func, int concurrency) {
            return Observable.just(last())
                .cast(BrowseableDTO.class)
                .flatMapIterable(BrowseableDTO::getLinks)
                .filter(link -> link.getRel().equals(rel))
                .map(Link::getHref)
                .flatMap(x -> Observable.defer(() ->func.apply(x)), true, concurrency)
                .map(object -> new Context(this, object));
        }
    }

    static public <T> Observable<Context> browserFor(Observable<String> links, Function<String,Observable<T>> func, int concurrency) {
        return links.flatMap(x -> Observable.defer(() -> func.apply(x)), true, concurrency).map( object -> new Context(object));
    }
}
