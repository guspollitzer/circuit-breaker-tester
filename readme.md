# Circuit breakers tester and comparator 

This app test many circuit breaker instances and compares their performance.
Currently, only 3 types of circuit breakers are being compared:
- the CB of the [resilience4j library](https://resilience4j.readme.io/docs/circuitbreaker);
- the CB of the MeLi's [toolkit resilience library](https://github.com/mercadolibre/fury_java-melitk-resilience/tree/master/resilience-core);
- and a custom CB implemented here, in this artifact.

Many instances of the same type may be tested simultaneously. Usually with different configurations.

## Usage
For each circuit breaker instance you want to include in the test, you have to implement a function that calls to the simulated service through the circuit breaker.
Said function should conform to the `Facade` interface, whose purpose is to homogenise the usage of different types of circuit breakers.  
For example, the implementation of the decorated service call for a resilience4j circuit breaker's instance would be something similar to:

```java
class Example {
   void main() {
      // Create a tester whose simulated service takes 8 milliseconds to respond, causing a paralleism of eight (the decorated service is called other seven times before responding the first).  
      var tester = new Tester(8);

      final var defaultCb4jConfig = new io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.Builder().build();
      final var defaultCb4j = io.github.resilience4j.circuitbreaker.CircuitBreaker.of("default resilience4J breaker",
              defaultCb4jConfig
      );
      final Tester.Facade defaultResilience4JFacade = (Tester.Request request) -> {
         Optional<String> oResult;
         try {
            final var result = defaultCb4j.executeSupplier(() -> {
               final var r = tester.simulatedServiceCall(request.milli);
               return request.isOk ? r : "fail";
            });
            oResult = Optional.of(result);
         } catch (CallNotPermittedException e) {
            oResult = Optional.empty();
         } catch (Exception e) {
            e.printStackTrace();
            oResult = Optional.empty();
         }
         return new Tester.Out("default resilience4J breaker", request, oResult);
      };

      tester.run(Collections.singletonList(defaultResilience4JFacade));
   }
}
```
The previous example includes the creation and execution of the test for a single circuit breaker instance.


## How does the tester work?

1. First, all the CB instances under test are created and initialized.
   For each CB, a facade of the service call decorated with the CB is created.
   The service is simulated with a method that returns a `String` after waiting some time.
   To make the test more exhaustive, the time that the simulated service waits to respond can be configured to be P times the period in which requests are generated causing the service to receive a new request while still responding to previous P's.
   We call that P the "parallelism". 

2. Then, every millisecond during the course of the test, a request is created and sent to all the CBs.
   The decision whenever the service responds successfully or not to that request is determined when the request is created and is the same for all the CBs.
   Therefore, all the CBs are tested with the same service behavior.
   
3. And finally, for each CB, the following statistics are captured:
   
   - call hits: how many times the CB was closed and the service responded successfully.
   - call misses: how many times the CB was closed and the service failed.
   - drop hits: how many times the CB was open and the service would have failed if it were called.
   - drop misses: how many times the CB was open and the service would have responded successfully if it were called.

The score of the CB is the proportion of hits (calls hits and drops hits) respect to the total number of request.
Another indicator of the performance of the CB is the proportion of request on which it was in closed state compared with the proportion of request to which the service would respond successfully.

## About the service simulator behaviour
As mentioned earlier, the decision of whenever the service simulator responds successfully or not is determined when the request is created.
To resemble the behaviour of real services, the criteria that defines the simulated service's probability of success over time is not random but predefined.
The success behavior of the simulated service if build by concatenating three elementary behaviours over time:
   - plateau: the service always responds successfully
   - valley: the service never responds successfully
   - climb: the probability of success increases linearly from 0 to 1 over time.


## Some tests reports.
Three tests were done to a chosen set of circuit breaker instances. The three test generate the same amount of request (NUMBER_OF_TICKS=80_000) at the same frequency (one request per millisecond). The only difference between them is the behaviour of the simulated service. 

### Test #1: ⎵⎴⎵⎴
The service takes 8 milliseconds to respond (causing a parallelism of 8), and the probability of success over time is *valley - plateau - valley - plateau*, 20 seconds each.
```
Report
Test duration:80839
Sample successes: 50.00%
                name             hits            fails          tryHits         tryFails         dropHits        dropFails  closedTime
        alwaysClosed:  40000 (50.00%),  40000 (50.00%),  40000 (50.00%),  40000 (50.00%),      0 (  NaN%),      0 (  NaN%),    100.00%
       meLi Breaker1:  40000 (50.00%),  40000 (50.00%),  40000 (50.00%),  40000 (50.00%),      0 (  NaN%),      0 (  NaN%),    100.00%
       meLi Breaker2:  40000 (50.00%),  40000 (50.00%),  40000 (50.00%),  40000 (50.00%),      0 (  NaN%),      0 (  NaN%),    100.00%
       meLi Breaker3:  40000 (50.00%),  40000 (50.00%),  40000 (50.00%),  40000 (50.00%),      0 (  NaN%),      0 (  NaN%),    100.00%
       meLi Breaker4:  40000 (50.00%),  40000 (50.00%),  40000 (50.00%),  40000 (50.00%),      0 (  NaN%),      0 (  NaN%),    100.00%
       meLi Breaker5:  40000 (50.00%),  40000 (50.00%),  40000 (50.00%),  40000 (50.00%),      0 (  NaN%),      0 (  NaN%),    100.00%
       meLi Breaker6:  40000 (50.00%),  40000 (50.00%),  40000 (50.00%),  40000 (50.00%),      0 (  NaN%),      0 (  NaN%),    100.00%
          myBreaker1:  77188 (96.49%),   2812 ( 3.52%),  39097 (95.34%),   1909 ( 4.66%),  38091 (97.68%),    903 ( 2.32%),     51.26%
          myBreaker2:  76918 (96.15%),   3082 ( 3.85%),  38609 (95.80%),   1691 ( 4.20%),  38309 (96.50%),   1391 ( 3.50%),     50.38%
          myBreaker3:  78428 (98.04%),   1572 ( 1.97%),  39759 (96.76%),   1331 ( 3.24%),  38669 (99.38%),    241 ( 0.62%),     51.36%
          myBreaker4:  77527 (96.91%),   2473 ( 3.09%),  39392 (95.48%),   1865 ( 4.52%),  38135 (98.43%),    608 ( 1.57%),     51.57%
          myBreaker5:  77941 (97.43%),   2059 ( 2.57%),  39451 (96.31%),   1510 ( 3.69%),  38490 (98.59%),    549 ( 1.41%),     51.20%
          myBreaker6:  77949 (97.44%),   2051 ( 2.56%),  39293 (96.69%),   1344 ( 3.31%),  38656 (98.20%),    707 ( 1.80%),     50.80%
       res4j config1:  71909 (89.89%),   8091 (10.11%),  38784 (84.94%),   6875 (15.06%),  33125 (96.46%),   1216 ( 3.54%),     57.07%
       res4j config2:  72044 (90.06%),   7956 ( 9.95%),  39035 (84.81%),   6991 (15.19%),  33009 (97.16%),    965 ( 2.84%),     57.53%
       res4j config3:  71954 (89.94%),   8046 (10.06%),  39020 (84.67%),   7066 (15.33%),  32934 (97.11%),    980 ( 2.89%),     57.61%
       res4j config5:  74662 (93.33%),   5338 ( 6.67%),  38610 (90.72%),   3948 ( 9.28%),  36052 (96.29%),   1390 ( 3.71%),     53.20%
       res4j config6:  74806 (93.51%),   5194 ( 6.49%),  38865 (90.54%),   4059 ( 9.46%),  35941 (96.94%),   1135 ( 3.06%),     53.66%
       res4j config7:  74964 (93.71%),   5036 ( 6.30%),  39082 (90.47%),   4118 ( 9.53%),  35882 (97.51%),    918 ( 2.49%),     54.00%
```

### Test #2: ╱⎴╱⎴
The service takes 8 milliseconds to respond (causing a parallelism of 8), and the probability of success over time is *climb - plateau - climb - plateau*, 20 seconds each, 80 seconds in total.
```
Report
Test duration:83737
Sample successes: 75.01%
                name             hits            fails          tryHits         tryFails         dropHits        dropFails  closedTime
        alwaysClosed:  60011 (75.01%),  19989 (24.99%),  60011 (75.01%),  19989 (24.99%),      0 (  NaN%),      0 (  NaN%),    100.00%
       meLi Breaker1:  60011 (75.01%),  19989 (24.99%),  60011 (75.01%),  19989 (24.99%),      0 (  NaN%),      0 (  NaN%),    100.00%
       meLi Breaker2:  60011 (75.01%),  19989 (24.99%),  60011 (75.01%),  19989 (24.99%),      0 (  NaN%),      0 (  NaN%),    100.00%
       meLi Breaker3:  60011 (75.01%),  19989 (24.99%),  60011 (75.01%),  19989 (24.99%),      0 (  NaN%),      0 (  NaN%),    100.00%
       meLi Breaker4:  60011 (75.01%),  19989 (24.99%),  60011 (75.01%),  19989 (24.99%),      0 (  NaN%),      0 (  NaN%),    100.00%
       meLi Breaker5:  60011 (75.01%),  19989 (24.99%),  60011 (75.01%),  19989 (24.99%),      0 (  NaN%),      0 (  NaN%),    100.00%
       meLi Breaker6:  60011 (75.01%),  19989 (24.99%),  60011 (75.01%),  19989 (24.99%),      0 (  NaN%),      0 (  NaN%),    100.00%
          myBreaker1:  66687 (83.36%),  13313 (16.64%),  56755 (84.95%),  10057 (15.05%),   9932 (75.31%),   3256 (24.69%),     83.52%
          myBreaker2:  67421 (84.28%),  12579 (15.72%),  56066 (86.66%),   8634 (13.34%),  11355 (74.22%),   3945 (25.78%),     80.88%
          myBreaker3:  68188 (85.24%),  11812 (14.77%),  55902 (87.89%),   7703 (12.11%),  12286 (74.94%),   4109 (25.06%),     79.51%
          myBreaker4:  66259 (82.82%),  13741 (17.18%),  56645 (84.52%),  10375 (15.48%),   9614 (74.07%),   3366 (25.93%),     83.78%
          myBreaker5:  67436 (84.30%),  12564 (15.71%),  55875 (86.89%),   8428 (13.11%),  11561 (73.65%),   4136 (26.35%),     80.38%
          myBreaker6:  68109 (85.14%),  11891 (14.86%),  55063 (88.80%),   6943 (11.20%),  13046 (72.50%),   4948 (27.50%),     77.51%
       res4j config1:  62410 (78.01%),  17590 (21.99%),  45916 (92.93%),   3495 ( 7.07%),  16494 (53.92%),  14095 (46.08%),     61.76%
       res4j config2:  64136 (80.17%),  15864 (19.83%),  48131 (92.36%),   3984 ( 7.64%),  16005 (57.40%),  11880 (42.60%),     65.14%
       res4j config3:  65928 (82.41%),  14072 (17.59%),  50649 (91.49%),   4710 ( 8.51%),  15279 (62.01%),   9362 (37.99%),     69.20%
       res4j config5:  61800 (77.25%),  18200 (22.75%),  44021 (95.22%),   2210 ( 4.78%),  17779 (52.65%),  15990 (47.35%),     57.79%
       res4j config6:  64222 (80.28%),  15778 (19.72%),  46808 (94.79%),   2575 ( 5.21%),  17414 (56.88%),  13203 (43.12%),     61.73%
       res4j config7:  65906 (82.38%),  14094 (17.62%),  49050 (94.00%),   3133 ( 6.00%),  16856 (60.60%),  10961 (39.40%),     65.23%
```

### Test #3: ⎵⎴╱⎴
The service takes 8 milliseconds to respond (causing a parallelism of 8), and the probability of success over time is *valley - plateau - climb - plateau*, 20 seconds each, 80 seconds in total.
```
Report
Test duration:83725
Sample successes: 62.52%
                name             hits            fails          tryHits         tryFails         dropHits        dropFails  closedTime
        alwaysClosed:  50014 (62.52%),  29986 (37.48%),  50014 (62.52%),  29986 (37.48%),      0 (  NaN%),      0 (  NaN%),    100.00%
       meLi Breaker1:  50014 (62.52%),  29986 (37.48%),  50014 (62.52%),  29986 (37.48%),      0 (  NaN%),      0 (  NaN%),    100.00%
       meLi Breaker2:  50014 (62.52%),  29986 (37.48%),  50014 (62.52%),  29986 (37.48%),      0 (  NaN%),      0 (  NaN%),    100.00%
       meLi Breaker3:  50014 (62.52%),  29986 (37.48%),  50014 (62.52%),  29986 (37.48%),      0 (  NaN%),      0 (  NaN%),    100.00%
       meLi Breaker4:  50014 (62.52%),  29986 (37.48%),  50014 (62.52%),  29986 (37.48%),      0 (  NaN%),      0 (  NaN%),    100.00%
       meLi Breaker5:  50014 (62.52%),  29986 (37.48%),  50014 (62.52%),  29986 (37.48%),      0 (  NaN%),      0 (  NaN%),    100.00%
       meLi Breaker6:  50014 (62.52%),  29986 (37.48%),  50014 (62.52%),  29986 (37.48%),      0 (  NaN%),      0 (  NaN%),    100.00%
          myBreaker1:  72659 (90.82%),   7341 ( 9.18%),  48559 (89.19%),   5886 (10.81%),  24100 (94.31%),   1455 ( 5.69%),     68.06%
          myBreaker2:  73450 (91.81%),   6550 ( 8.19%),  48271 (90.94%),   4807 ( 9.06%),  25179 (93.53%),   1743 ( 6.47%),     66.35%
          myBreaker3:  73731 (92.16%),   6269 ( 7.84%),  47927 (91.97%),   4182 ( 8.03%),  25804 (92.52%),   2087 ( 7.48%),     65.14%
          myBreaker4:  72855 (91.07%),   7145 ( 8.93%),  48205 (90.03%),   5336 ( 9.97%),  24650 (93.16%),   1809 ( 6.84%),     66.93%
          myBreaker5:  73142 (91.43%),   6858 ( 8.57%),  47634 (91.41%),   4478 ( 8.59%),  25508 (91.47%),   2380 ( 8.53%),     65.14%
          myBreaker6:  73419 (91.77%),   6581 ( 8.23%),  47443 (92.21%),   4010 ( 7.79%),  25976 (90.99%),   2571 ( 9.01%),     64.32%
       res4j config1:  67234 (84.04%),  12766 (15.96%),  42481 (89.03%),   5233 (10.97%),  24753 (76.67%),   7533 (23.33%),     59.64%
       res4j config2:  68523 (85.65%),  11477 (14.35%),  44206 (88.63%),   5669 (11.37%),  24317 (80.72%),   5808 (19.28%),     62.34%
       res4j config3:  69373 (86.72%),  10627 (13.28%),  45304 (88.45%),   5917 (11.55%),  24069 (83.63%),   4710 (16.37%),     64.03%
       res4j config5:  68933 (86.17%),  11067 (13.83%),  42016 (93.19%),   3069 ( 6.81%),  26917 (77.09%),   7998 (22.91%),     56.36%
       res4j config6:  70081 (87.60%),   9919 (12.40%),  43536 (92.68%),   3441 ( 7.32%),  26545 (80.38%),   6478 (19.62%),     58.72%
       res4j config7:  70722 (88.40%),   9278 (11.60%),  44539 (92.13%),   3803 ( 7.87%),  26183 (82.71%),   5475 (17.29%),     60.43%
```

#### A variant of the test #3 with no parallelism:
```
Report
Test duration:80121
Sample successes: 62.50%
                name             hits            fails          tryHits         tryFails         dropHits        dropFails  closedTime
        alwaysClosed:  49998 (62.50%),  30002 (37.50%),  49998 (62.50%),  30002 (37.50%),      0 (  NaN%),      0 (  NaN%),    100.00%
       meLi Breaker1:  49998 (62.50%),  30002 (37.50%),  49998 (62.50%),  30002 (37.50%),      0 (  NaN%),      0 (  NaN%),    100.00%
       meLi Breaker2:  49998 (62.50%),  30002 (37.50%),  49998 (62.50%),  30002 (37.50%),      0 (  NaN%),      0 (  NaN%),    100.00%
       meLi Breaker3:  49998 (62.50%),  30002 (37.50%),  49998 (62.50%),  30002 (37.50%),      0 (  NaN%),      0 (  NaN%),    100.00%
       meLi Breaker4:  49998 (62.50%),  30002 (37.50%),  49998 (62.50%),  30002 (37.50%),      0 (  NaN%),      0 (  NaN%),    100.00%
       meLi Breaker5:  49998 (62.50%),  30002 (37.50%),  49998 (62.50%),  30002 (37.50%),      0 (  NaN%),      0 (  NaN%),    100.00%
       meLi Breaker6:  49998 (62.50%),  30002 (37.50%),  49998 (62.50%),  30002 (37.50%),      0 (  NaN%),      0 (  NaN%),    100.00%
          myBreaker1:  74750 (93.44%),   5250 ( 6.56%),  46796 (95.81%),   2048 ( 4.19%),  27954 (89.72%),   3202 (10.28%),     61.06%
          myBreaker2:  74729 (93.41%),   5271 ( 6.59%),  46664 (96.01%),   1937 ( 3.99%),  28065 (89.38%),   3334 (10.62%),     60.75%
          myBreaker3:  74050 (92.56%),   5950 ( 7.44%),  45009 (97.91%),    961 ( 2.09%),  29041 (85.34%),   4989 (14.66%),     57.46%
          myBreaker4:  74592 (93.24%),   5408 ( 6.76%),  46262 (96.51%),   1672 ( 3.49%),  28330 (88.35%),   3736 (11.65%),     59.92%
          myBreaker5:  74531 (93.16%),   5469 ( 6.84%),  46061 (96.78%),   1532 ( 3.22%),  28470 (87.85%),   3937 (12.15%),     59.49%
          myBreaker6:  73930 (92.41%),   6070 ( 7.59%),  44765 (98.16%),    837 ( 1.84%),  29165 (84.79%),   5233 (15.21%),     57.00%
       res4j config1:  71419 (89.27%),   8581 (10.73%),  42741 (97.00%),   1324 ( 3.00%),  28678 (79.81%),   7257 (20.19%),     55.08%
       res4j config2:  72507 (90.63%),   7493 ( 9.37%),  44127 (96.45%),   1622 ( 3.55%),  28380 (82.86%),   5871 (17.14%),     57.19%
       res4j config3:  73452 (91.82%),   6548 ( 8.19%),  45504 (95.68%),   2054 ( 4.32%),  27948 (86.15%),   4494 (13.85%),     59.45%
       res4j config5:  71224 (89.03%),   8776 (10.97%),  41937 (98.32%),    715 ( 1.68%),  29287 (78.42%),   8061 (21.58%),     53.32%
       res4j config6:  72403 (90.50%),   7597 ( 9.50%),  43347 (97.86%),    946 ( 2.14%),  29056 (81.37%),   6651 (18.63%),     55.37%
       res4j config7:  73532 (91.92%),   6468 ( 8.09%),  44972 (96.89%),   1442 ( 3.11%),  28560 (85.04%),   5026 (14.96%),     58.02%```
```
#### A variant of test #3 with a parallelism of 64:
```
Report
Test duration:85079
Sample successes: 62.56%
                name             hits            fails          tryHits         tryFails         dropHits        dropFails  closedTime
        alwaysClosed:  50046 (62.56%),  29954 (37.44%),  50046 (62.56%),  29954 (37.44%),      0 (  NaN%),      0 (  NaN%),    100.00%
       meLi Breaker1:  50046 (62.56%),  29954 (37.44%),  50046 (62.56%),  29954 (37.44%),      0 (  NaN%),      0 (  NaN%),    100.00%
       meLi Breaker2:  50046 (62.56%),  29954 (37.44%),  50046 (62.56%),  29954 (37.44%),      0 (  NaN%),      0 (  NaN%),    100.00%
       meLi Breaker3:  50046 (62.56%),  29954 (37.44%),  50046 (62.56%),  29954 (37.44%),      0 (  NaN%),      0 (  NaN%),    100.00%
       meLi Breaker4:  50046 (62.56%),  29954 (37.44%),  50046 (62.56%),  29954 (37.44%),      0 (  NaN%),      0 (  NaN%),    100.00%
       meLi Breaker5:  50046 (62.56%),  29954 (37.44%),  50046 (62.56%),  29954 (37.44%),      0 (  NaN%),      0 (  NaN%),    100.00%
       meLi Breaker6:  50046 (62.56%),  29954 (37.44%),  50046 (62.56%),  29954 (37.44%),      0 (  NaN%),      0 (  NaN%),    100.00%
          myBreaker1:  69037 (86.30%),  10963 (13.70%),  49991 (82.09%),  10908 (17.91%),  19046 (99.71%),     55 ( 0.29%),     76.12%
          myBreaker2:  69101 (86.38%),  10899 (13.62%),  50034 (82.13%),  10887 (17.87%),  19067 (99.94%),     12 ( 0.06%),     76.15%
          myBreaker3:  72447 (90.56%),   7553 ( 9.44%),  48822 (88.52%),   6329 (11.48%),  23625 (95.07%),   1224 ( 4.93%),     68.94%
          myBreaker4:  68454 (85.57%),  11546 (14.43%),  49963 (81.34%),  11463 (18.66%),  18491 (99.55%),     83 ( 0.45%),     76.78%
          myBreaker5:  68785 (85.98%),  11215 (14.02%),  50041 (81.70%),  11210 (18.30%),  18744 (99.97%),      5 ( 0.03%),     76.56%
          myBreaker6:  71992 (89.99%),   8008 (10.01%),  48631 (88.06%),   6593 (11.94%),  23361 (94.29%),   1415 ( 5.71%),     69.03%
       res4j config1:  69825 (87.28%),  10175 (12.72%),  44048 (91.34%),   4177 ( 8.66%),  25777 (81.12%),   5998 (18.88%),     60.28%
       res4j config2:  70228 (87.79%),   9772 (12.22%),  44775 (90.87%),   4501 ( 9.13%),  25453 (82.84%),   5271 (17.16%),     61.60%
       res4j config3:  71235 (89.04%),   8765 (10.96%),  45728 (91.14%),   4447 ( 8.86%),  25507 (85.52%),   4318 (14.48%),     62.72%
       res4j config5:  70201 (87.75%),   9799 (12.25%),  43505 (93.03%),   3258 ( 6.97%),  26696 (80.32%),   6541 (19.68%),     58.45%
       res4j config6:  70880 (88.60%),   9120 (11.40%),  44319 (92.89%),   3393 ( 7.11%),  26561 (82.26%),   5727 (17.74%),     59.64%
       res4j config7:  71584 (89.48%),   8416 (10.52%),  44902 (93.21%),   3272 ( 6.79%),  26682 (83.84%),   5144 (16.16%),     60.22%
```

### Conclusion
According to these results, with the given configuration, the meli CB does not work at all, and the other two work fine with similar performances: the resilience 4j CB has slightly better performance when the parallelism is very high (above 50), and my custom CB has slightly better performance when the parallelism is medium or low parallelism (below 50).
