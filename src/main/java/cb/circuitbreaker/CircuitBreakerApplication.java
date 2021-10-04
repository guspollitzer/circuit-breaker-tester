package cb.circuitbreaker;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.ToString;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CircuitBreakerApplication {

	private static final Random RANDOM = new Random();
	private static final int PERIOD = 1000;
	private static final int PARALLELISM = 10;

	@SneakyThrows
	public static void main(String[] args) {

		var breaker = new CircuitBreakerSync(0.5, 1, 0.2, System::nanoTime);

		var graph = Flux.interval(Duration.ofMillis(1))
				.takeWhile(milli -> milli < 8000)
				.map(milli -> new Request(milli, isOk(milli)))
				.parallel(PARALLELISM)
				.runOn(Schedulers.newBoundedElastic(PARALLELISM, 4, "myScheduler", 1, true))
				.map(request -> {
					var response = breaker.apply(
							() -> work(request.milli),
							r -> request.isOk,
							new CircuitBreaker.StateChangeListener() {
								@Override
								public void brokenStateChanged(boolean isBroken) {
									print("%d - open=%b\n", request.milli, isBroken);
								}

								@Override
								public void failuresProportionChanged(double newValue) {
									print("%d - failProp=%f\n", request.milli, newValue);
								}

								@Override
								public void triesChanged(int newValue) {
									print("%d - tries=%d\n", request.milli, newValue);

								}
							}
					);
					return new Out(request, response);
				})
				.sequential()
				.doOnNext(out -> print("%d - out=%s\n", out.request.milli, out.response.toString()))
				.reduce(
						new Accum(),
						(accum, out) -> {
							if (out.request.isOk && out.response.isPresent()) {
								accum.tryHits += 1;
							}
							if (out.request.isOk && out.response.isEmpty()) {
								accum.tryFails += 1;
							}
							if (!out.request.isOk && out.response.isEmpty()) {
								accum.dropHits += 1;
							}
							if (!out.request.isOk && out.response.isPresent()) {
								accum.dropFails += 1;
							}
							return accum;
						}
				);

		print("accum=%s", graph.toFuture().join());
		printer.shutdown();
	}

	static class Accum {
		int tryHits;
		int tryFails;
		int dropHits;
		int dropFails;

		public String toString() {
			return String.format("Accum(tryHits=%d (%2.2f%%), tryFails=%d (%2.2f%%), dropHits=%d (%2.2f%%), dropFails=%d (%2.2f%%), sampleSuccesses=%2.2f%%, closedTime=%2.2f%%)",
					tryHits, tryHits * 100.0 / (tryHits + tryFails),
					tryFails, tryFails * 100.0 / (tryHits + tryFails),
					dropHits, dropHits * 100.0 / (dropHits + dropFails),
					dropFails, dropFails * 100.0 / (dropHits + dropFails),
					(tryHits + tryFails) * 100.0/ (tryHits + tryFails + dropHits + dropFails),
					(tryHits + dropFails) * 100.0 / (tryHits + dropFails + tryFails + dropHits)
			);
		}
	}

	@ToString
	@RequiredArgsConstructor
	static class Request {
		final long milli;
		final boolean isOk;
	}

	@ToString
	@RequiredArgsConstructor
	static class Out {
		final Request request;
		final Optional<String> response;
	}

	static String work(long milli) {
		try {
			Thread.sleep(PARALLELISM * 9 / 10);
			return Long.toString(milli);
		} catch (InterruptedException e) {
			return String.format("work sleep interrupted at %s\n", milli);
		}
	}

	static boolean isOk(long milli) {
		var millisSincePeriodStart = milli % PERIOD;
		final boolean ok;
		if ((milli / PERIOD) % 2 == 1) {
			ok = RANDOM.nextInt(PERIOD) < millisSincePeriodStart;
		} else {
			ok = false;
		}
		print("%d - isOk=%b\n", milli, ok);
		return ok;
	}

	////


	static ExecutorService printer = Executors.newSingleThreadExecutor();

	static void print(String format, Object... args) {
		printer.execute(() -> System.out.printf(format, args));
	}

}
