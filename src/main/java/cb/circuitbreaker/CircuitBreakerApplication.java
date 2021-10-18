package cb.circuitbreaker;

import com.mercadolibre.resilience.breaker.Action;
import com.mercadolibre.resilience.breaker.CircuitBreakers;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import lombok.SneakyThrows;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static cb.circuitbreaker.Printer.debug;

public class CircuitBreakerApplication {

	private static final int PARALLELISM = 8;

	@SneakyThrows
	public static void main(String[] args) {
		new CircuitBreakerApplication().start();
		Printer.shutdown();
	}

	void start() {
		// Create the instances of CircuitBreakerSync that will be tested and compared, and wrap them within a Facade to homogenize the usage.
		final Stream<Tester.Facade> myBreakerFacades;
		{
			var breaker1 = new CircuitBreakerSync(0.5, 32, 0.02, System::nanoTime);
			var breaker2 = new CircuitBreakerSync(0.5, 64, 0.02, System::nanoTime);
			var breaker3 = new CircuitBreakerSync(0.5, 128, 0.02, System::nanoTime);
			var breaker4 = new CircuitBreakerSync(0.5, 32, 0.05, System::nanoTime);
			var breaker5 = new CircuitBreakerSync(0.5, 64, 0.05, System::nanoTime);
			var breaker6 = new CircuitBreakerSync(0.5, 128, 0.05, System::nanoTime);
			var myBreakers = Map.of(
					"myBreaker1", breaker1,
					"myBreaker2", breaker2,
					"myBreaker3", breaker3,
					"myBreaker4", breaker4,
					"myBreaker5", breaker5,
					"myBreaker6", breaker6
			);
			myBreakerFacades = myBreakers.entrySet().stream()
					.map(entry -> buildAFacadeForACircuitBreakerSync(entry.getKey(), entry.getValue()));
		}

		// Create the instances of MeLi circuit breaker that will be tested and compared, and wrap them within a Facade to homogenize the usage.
		final Stream<Tester.Facade> meliBreakerFacakdes;
		{
			var meliBreaker1 = CircuitBreakers.newExponentialBreaker("1", 100, 60, 0.2, 2, 60, 0.5, 1, 2);
			var meliBreaker2 = CircuitBreakers.newExponentialBreaker("2", 100, 120, 0.2, 4, 60, 0.5, 1, 2);
			var meliBreaker3 = CircuitBreakers.newExponentialBreaker("3", 100, 240, 0.2, 8, 60, 0.5, 1, 2);
			var meliBreaker4 = CircuitBreakers.newExponentialBreaker("4", 10, 60, 0.2, 2, 60, 0.5, 1, 2);
			var meliBreaker5 = CircuitBreakers.newExponentialBreaker("5", 10, 120, 0.2, 4, 60, 0.5, 1, 2);
			var meliBreaker6 = CircuitBreakers.newExponentialBreaker("6", 10, 240, 0.2, 8, 60, 0.5, 1, 2);
			var meliBreakers = Map.of(
					"meLi Breaker1", meliBreaker1,
					"meLi Breaker2", meliBreaker2,
					"meLi Breaker3", meliBreaker3,
					"meLi Breaker4", meliBreaker4,
					"meLi Breaker5", meliBreaker5,
					"meLi Breaker6", meliBreaker6
			);
			meliBreakerFacakdes = meliBreakers.entrySet().stream()
					.map(entry -> buildAFacadeForAMeliCircuitBreaker(entry.getKey(), entry.getValue()));
		}

		// Create the instances of resilence 4j circuit breaker that will be tested and compared, and wrap them within a Facade to homogenize the usage.
		final Stream<Tester.Facade> res4jBreakerFacades;
		{
			var resConfig1 = new CircuitBreakerConfig.Builder()
					.slidingWindow(4, 1, CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
					.waitDurationInOpenState(Duration.ofMillis(32))
					.minimumNumberOfCalls(1)
					.recordResult("fail"::equals)
					.build();
			var resConfig2 = new CircuitBreakerConfig.Builder()
					.slidingWindow(8, 2, CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
					.waitDurationInOpenState(Duration.ofMillis(32))
					.minimumNumberOfCalls(1)
					.recordResult("fail"::equals)
					.build();
			var resConfig3 = new CircuitBreakerConfig.Builder()
					.slidingWindow(16, 4, CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
					.waitDurationInOpenState(Duration.ofMillis(32))
					.minimumNumberOfCalls(1)
					.recordResult("fail"::equals)
					.build();
			var resConfig5 = new CircuitBreakerConfig.Builder()
					.slidingWindow(4, 1, CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
					.waitDurationInOpenState(Duration.ofMillis(64))
					.minimumNumberOfCalls(1)
					.recordResult("fail"::equals)
					.build();
			var resConfig6 = new CircuitBreakerConfig.Builder()
					.slidingWindow(8, 2, CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
					.waitDurationInOpenState(Duration.ofMillis(64))
					.minimumNumberOfCalls(1)
					.recordResult("fail"::equals)
					.build();
			var resConfig7 = new CircuitBreakerConfig.Builder()
					.slidingWindow(16, 4, CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
					.waitDurationInOpenState(Duration.ofMillis(64))
					.minimumNumberOfCalls(1)
					.recordResult("fail"::equals)
					.build();
			var resConfigs = Map.of(
					"res4j config1", resConfig1,
					"res4j config2", resConfig2,
					"res4j config3", resConfig3,
					"res4j config5", resConfig5,
					"res4j config6", resConfig6,
					"res4j config7", resConfig7
			);
			res4jBreakerFacades = resConfigs.entrySet().stream()
					.map(entry -> buildAFacadeForARes4jBreaker(entry.getKey(), entry.getValue()));
		}

		// Create a facade with no circuit breaker (behaves like a circuit breaker that is always closed).
		var alwaysClosed = new Tester.Facade() {
			@Override
			public Tester.Out doSomething(Tester.Request request) {
				return new Tester.Out("alwaysClosed", request, Optional.of(Long.toString(request.milli)));
			}
		};

		// initialize the list that contains all the circuit breakers under test.
		var facades = Stream.concat(
				Stream.concat(Stream.of(alwaysClosed), myBreakerFacades),
				Stream.concat(meliBreakerFacakdes, res4jBreakerFacades)
		).collect(Collectors.toList());

		var tester = new Tester(PARALLELISM, facades);
		tester.run();
	}


	/** Builds a {@link Tester.Facade} for the synchronous version of my custom circuit breaker */
	Tester.Facade buildAFacadeForACircuitBreakerSync(String name, CircuitBreakerSync breaker) {
		return request -> {
			var response = breaker.execute(
					() -> work(request.milli),
					r -> request.isOk,
					new CircuitBreaker.StateChangeListener() {
						@Override
						public void brokenStateChanged(boolean isBroken) {
							debug("%d - %s - open=%b\n", request.milli, name, isBroken);
						}

						@Override
						public void failuresProportionChanged(double newValue) {
							debug("%d - %s - failProp=%f\n", request.milli, name, newValue);
						}

						@Override
						public void triesChanged(int newValue) {
							debug("%d - %s - tries=%d\n", request.milli, name, newValue);

						}
					}
			);
			return new Tester.Out(name, request, response);
		};
	}

	/** Builds a {@link Tester.Facade} for a MeLi circuit breaker */
	Tester.Facade buildAFacadeForAMeliCircuitBreaker(String name, com.mercadolibre.resilience.breaker.CircuitBreaker cb) {
		return request -> {
			Optional<String> oResult;
			try {
				var result = cb.run(new Action<String>() {
					@Override
					public boolean isValid(String result, Throwable t) {
						return request.isOk;
					}

					@Override
					public String get() {
						return work(request.milli);
					}
				});
				oResult = Optional.of(result);
			} catch (RejectedExecutionException e) {
				oResult = Optional.empty();
			} catch (ExecutionException e) {
				e.printStackTrace();
				oResult = Optional.empty();
			}
			return new Tester.Out(name, request, oResult);
		};
	}

	/** Builds a {@link Tester.Facade} for a resilience 4j circuit breaker */
	private Tester.Facade buildAFacadeForARes4jBreaker(String name, CircuitBreakerConfig cbc) {
		final var cb = io.github.resilience4j.circuitbreaker.CircuitBreaker.of(name, cbc);
		return request -> {
			Optional<String> oResult;
//			print("state of %s before is: %s\n\t; metrics: failuresRate=%f, failures=%d\n", name, cb.getState(), cb.getMetrics().getFailureRate(), cb.getMetrics().getNumberOfFailedCalls());
			try {
				var result = cb.executeSupplier(() -> {
					var r = work(request.milli);
					return request.isOk ? r : "fail";
				});
				oResult = Optional.of(result);
			} catch (CallNotPermittedException e) {
				oResult = Optional.empty();
			} catch (Exception e) {
				e.printStackTrace();
				oResult = Optional.empty();
//			} finally {
//				print("state of %s after is: %s\n\t; metrics: failuresRate=%f, failures=%d\n", name, cb.getState(), cb.getMetrics().getFailureRate(), cb.getMetrics().getNumberOfFailedCalls());
			}
			return new Tester.Out(name, request, oResult);
		};
	}


	/** Simulates de work that the decorated service does.
	 * @return the received long converted to String after waiting some time. */
	String work(long milli) {
		try {
			if (PARALLELISM > 1) {
				Thread.sleep(PARALLELISM);
			}
			return Long.toString(milli);
		} catch (InterruptedException e) {
			return String.format("work sleep interrupted at %s\n", milli);
		}
	}

}
