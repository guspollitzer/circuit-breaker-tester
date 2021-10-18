# Circuit breakers tester and comparator 

This app test many circuit breaker instances and compares their performance.
Currently, only 3 types of circuit breakers are being compared:
- the CB of the [resilience4j library](https://resilience4j.readme.io/docs/circuitbreaker);
- the CB of the MeLi's [toolkit resilience library](https://github.com/mercadolibre/fury_java-melitk-resilience/tree/master/resilience-core);
- and a custom CB implemented here, in this artifact.

Many instances of the same type may be tested simultaneously. Usually with different configurations.

## How does the tester work?

There are two testers: one for synchronous services and the other for asynchronous ones.
The following explanation is for the first one. The description for the second is easily derived.

1. First, all the CB instances under test are created and initialized.
   For each CB, a facade of the service call decorated with the CB is created.
   The service is simulated with a method that returns a `String` after waiting some time.
   To make the test more exhaustive, the time that the simulated service waits to respond can be configured to be P times the period in which requests are generated causing the service to receive a new request while still responding to previous P's.
   We call that P the "parallelism". 

2. Then, every millisecond during the course of the test, a request is created and sent to all the CBs.
   The decision whenever the service responds successfully or not to that request is determined when the request is created and is the same for all the CBs.
   Therefore, all the CBs are tested with the same requests and responses.
   
3. And finally, for each CB, the following statistics are captured:
   
   - call hits: how many times the CB was closed and the service responded successfully.
   - call misses: how many times the CB was closed and the service failed.
   - drop hits: how many times the CB was open and the service would have failed if it were called.
   - drop misses: how many times the CB was open and the service would have responded successfully if it were called.

The score of the CB is the proportion of hits (calls hits and drops hits) respect to the total number of request.
Another indicator of the performance of the CB is the proportion of request on which it was in closed state compared with the proportion of request to which the service would respond successfully.

## The tester report

### With no parallelism:
```
Test duration:80793
Sample successes: 62.59%
                name             hits            fails          tryHits         tryFails         dropHits        dropFails  closedTime
       MeLi Breaker1:  50073 (62.59%),  29927 (37.41%),  50073 (62.59%),  29927 (37.41%),      0 (  NaN%),      0 (  NaN%),    100.00%
       MeLi Breaker2:  50073 (62.59%),  29927 (37.41%),  50073 (62.59%),  29927 (37.41%),      0 (  NaN%),      0 (  NaN%),    100.00%
       MeLi Breaker3:  50073 (62.59%),  29927 (37.41%),  50073 (62.59%),  29927 (37.41%),      0 (  NaN%),      0 (  NaN%),    100.00%
       MeLi Breaker4:  50073 (62.59%),  29927 (37.41%),  50073 (62.59%),  29927 (37.41%),      0 (  NaN%),      0 (  NaN%),    100.00%
       MeLi Breaker5:  50073 (62.59%),  29927 (37.41%),  50073 (62.59%),  29927 (37.41%),      0 (  NaN%),      0 (  NaN%),    100.00%
       MeLi Breaker6:  50073 (62.59%),  29927 (37.41%),  50073 (62.59%),  29927 (37.41%),      0 (  NaN%),      0 (  NaN%),    100.00%
        alwaysClosed:  50073 (62.59%),  29927 (37.41%),  50073 (62.59%),  29927 (37.41%),      0 (  NaN%),      0 (  NaN%),    100.00%
          myBreaker1:  73969 (92.46%),   6031 ( 7.54%),  47035 (94.02%),   2993 ( 5.98%),  26934 (89.86%),   3038 (10.14%),     62.54%
          myBreaker2:  72874 (91.09%),   7126 ( 8.91%),  45555 (94.59%),   2608 ( 5.41%),  27319 (85.81%),   4518 (14.19%),     60.20%
          myBreaker3:  72368 (90.46%),   7632 ( 9.54%),  44499 (95.58%),   2058 ( 4.42%),  27869 (83.33%),   5574 (16.67%),     58.20%
          myBreaker4:  73563 (91.95%),   6437 ( 8.05%),  45878 (95.34%),   2242 ( 4.66%),  27685 (86.84%),   4195 (13.16%),     60.15%
          myBreaker5:  73520 (91.90%),   6480 ( 8.10%),  45544 (95.89%),   1951 ( 4.11%),  27976 (86.07%),   4529 (13.93%),     59.37%
          myBreaker6:  72311 (90.39%),   7689 ( 9.61%),  43845 (96.78%),   1461 ( 3.22%),  28466 (82.05%),   6228 (17.95%),     56.63%
       res4j config1:  70661 (88.33%),   9339 (11.67%),  43037 (94.92%),   2303 ( 5.08%),  27624 (79.70%),   7036 (20.30%),     56.68%
       res4j config2:  71522 (89.40%),   8478 (10.60%),  44253 (94.33%),   2658 ( 5.67%),  27269 (82.41%),   5820 (17.59%),     58.64%
       res4j config3:  72244 (90.31%),   7756 ( 9.70%),  45277 (93.86%),   2960 ( 6.14%),  26967 (84.90%),   4796 (15.10%),     60.30%
       res4j config5:  70910 (88.64%),   9090 (11.36%),  42163 (97.28%),   1180 ( 2.72%),  28747 (78.42%),   7910 (21.58%),     54.18%
       res4j config6:  72033 (90.04%),   7967 ( 9.96%),  43513 (96.87%),   1407 ( 3.13%),  28520 (81.30%),   6560 (18.70%),     56.15%
       res4j config7:  72936 (91.17%),   7064 ( 8.83%),  44785 (96.19%),   1776 ( 3.81%),  28151 (84.19%),   5288 (15.81%),     58.20%
```

### When the parallelism is 8:
```
Test duration:80896
Sample successes: 62.45%
                name             hits            fails          tryHits         tryFails         dropHits        dropFails  closedTime
       MeLi Breaker1:  49961 (62.45%),  30039 (37.55%),  49961 (62.45%),  30039 (37.55%),      0 (  NaN%),      0 (  NaN%),    100.00%
       MeLi Breaker2:  49961 (62.45%),  30039 (37.55%),  49961 (62.45%),  30039 (37.55%),      0 (  NaN%),      0 (  NaN%),    100.00%
       MeLi Breaker3:  49961 (62.45%),  30039 (37.55%),  49961 (62.45%),  30039 (37.55%),      0 (  NaN%),      0 (  NaN%),    100.00%
       MeLi Breaker4:  49961 (62.45%),  30039 (37.55%),  49961 (62.45%),  30039 (37.55%),      0 (  NaN%),      0 (  NaN%),    100.00%
       MeLi Breaker5:  49961 (62.45%),  30039 (37.55%),  49961 (62.45%),  30039 (37.55%),      0 (  NaN%),      0 (  NaN%),    100.00%
       MeLi Breaker6:  49961 (62.45%),  30039 (37.55%),  49961 (62.45%),  30039 (37.55%),      0 (  NaN%),      0 (  NaN%),    100.00%
        alwaysClosed:  49961 (62.45%),  30039 (37.55%),  49961 (62.45%),  30039 (37.55%),      0 (  NaN%),      0 (  NaN%),    100.00%
          myBreaker1:  71011 (88.76%),   8989 (11.24%),  47681 (87.67%),   6709 (12.33%),  23330 (91.10%),   2280 ( 8.90%),     67.99%
          myBreaker2:  71257 (89.07%),   8743 (10.93%),  46890 (89.21%),   5672 (10.79%),  24367 (88.81%),   3071 (11.19%),     65.70%
          myBreaker3:  70929 (88.66%),   9071 (11.34%),  45424 (90.92%),   4534 ( 9.08%),  25505 (84.90%),   4537 (15.10%),     62.45%
          myBreaker4:  70769 (88.46%),   9231 (11.54%),  46832 (88.47%),   6102 (11.53%),  23937 (88.44%),   3129 (11.56%),     66.17%
          myBreaker5:  71752 (89.69%),   8248 (10.31%),  47216 (89.56%),   5503 (10.44%),  24536 (89.94%),   2745 (10.06%),     65.90%
          myBreaker6:  71538 (89.42%),   8462 (10.58%),  46295 (90.61%),   4796 ( 9.39%),  25243 (87.32%),   3666 (12.68%),     63.86%
       res4j config1:  66942 (83.68%),  13058 (16.32%),  42173 (88.89%),   5270 (11.11%),  24769 (76.08%),   7788 (23.92%),     59.30%
       res4j config2:  67811 (84.76%),  12189 (15.24%),  43228 (88.79%),   5456 (11.21%),  24583 (78.50%),   6733 (21.50%),     60.86%
       res4j config3:  68948 (86.19%),  11052 (13.82%),  44701 (88.53%),   5792 (11.47%),  24247 (82.17%),   5260 (17.83%),     63.12%
       res4j config5:  68156 (85.20%),  11844 (14.81%),  41168 (93.10%),   3051 ( 6.90%),  26988 (75.43%),   8793 (24.57%),     55.27%
       res4j config6:  69372 (86.72%),  10628 (13.29%),  42645 (92.79%),   3312 ( 7.21%),  26727 (78.51%),   7316 (21.49%),     57.45%
       res4j config7:  70204 (87.76%),   9796 (12.25%),  43866 (92.22%),   3701 ( 7.78%),  26338 (81.21%),   6095 (18.79%),     59.46%
```

### When the parallelism is 16:
```
Report
Test duration:81630
Sample successes: 62.49%
                name             hits            fails          tryHits         tryFails         dropHits        dropFails  closedTime
       MeLi Breaker1:  49991 (62.49%),  30009 (37.51%),  49991 (62.49%),  30009 (37.51%),      0 (  NaN%),      0 (  NaN%),    100.00%
       MeLi Breaker2:  49991 (62.49%),  30009 (37.51%),  49991 (62.49%),  30009 (37.51%),      0 (  NaN%),      0 (  NaN%),    100.00%
       MeLi Breaker3:  49991 (62.49%),  30009 (37.51%),  49991 (62.49%),  30009 (37.51%),      0 (  NaN%),      0 (  NaN%),    100.00%
       MeLi Breaker4:  49991 (62.49%),  30009 (37.51%),  49991 (62.49%),  30009 (37.51%),      0 (  NaN%),      0 (  NaN%),    100.00%
       MeLi Breaker5:  49991 (62.49%),  30009 (37.51%),  49991 (62.49%),  30009 (37.51%),      0 (  NaN%),      0 (  NaN%),    100.00%
       MeLi Breaker6:  49991 (62.49%),  30009 (37.51%),  49991 (62.49%),  30009 (37.51%),      0 (  NaN%),      0 (  NaN%),    100.00%
        alwaysClosed:  49991 (62.49%),  30009 (37.51%),  49991 (62.49%),  30009 (37.51%),      0 (  NaN%),      0 (  NaN%),    100.00%
          myBreaker1:  69484 (86.86%),  10516 (13.15%),  47573 (85.45%),   8098 (14.55%),  21911 (90.06%),   2418 ( 9.94%),     69.59%
          myBreaker2:  69777 (87.22%),  10223 (12.78%),  46515 (87.33%),   6747 (12.67%),  23262 (87.00%),   3476 (13.00%),     66.58%
          myBreaker3:  72250 (90.31%),   7750 ( 9.69%),  47539 (89.97%),   5298 (10.03%),  24711 (90.97%),   2452 ( 9.03%),     66.05%
          myBreaker4:  69180 (86.48%),  10820 (13.53%),  47271 (85.37%),   8100 (14.63%),  21909 (88.96%),   2720 (11.04%),     69.21%
          myBreaker5:  71609 (89.51%),   8391 (10.49%),  47799 (88.52%),   6199 (11.48%),  23810 (91.57%),   2192 ( 8.43%),     67.50%
          myBreaker6:  71666 (89.58%),   8334 (10.42%),  47140 (89.58%),   5483 (10.42%),  24526 (89.59%),   2851 (10.41%),     65.78%
       res4j config1:  67430 (84.29%),  12570 (15.71%),  42677 (89.03%),   5256 (10.97%),  24753 (77.19%),   7314 (22.81%),     59.92%
       res4j config2:  68202 (85.25%),  11798 (14.75%),  43769 (88.70%),   5576 (11.30%),  24433 (79.70%),   6222 (20.30%),     61.68%
       res4j config3:  68931 (86.16%),  11069 (13.84%),  44631 (88.66%),   5709 (11.34%),  24300 (81.93%),   5360 (18.07%),     62.93%
       res4j config5:  68234 (85.29%),  11766 (14.71%),  41470 (92.74%),   3245 ( 7.26%),  26764 (75.85%),   8521 (24.15%),     55.89%
       res4j config6:  69698 (87.12%),  10302 (12.88%),  43152 (92.57%),   3463 ( 7.43%),  26546 (79.51%),   6839 (20.49%),     58.27%
       res4j config7:  70259 (87.82%),   9741 (12.18%),  43937 (92.26%),   3687 ( 7.74%),  26322 (81.30%),   6054 (18.70%),     59.53%
```
### Conclusion
According to these results, with the given configuration, the meli CB does not work at all, and the other two have similar performances when no parallelism is involved, but the custom one is better when the parallelism increases. 