# jyturtle
Because making a game with the turtle library is pretty much impossible.

In a Nutshell
---
I made this in two days so my classmates wouldn't have to suffer.

This is a skeletal version of the [turtle](https://docs.python.org/3/library/turtle.html) library. If there's one thing this does better, it's not delaying.

Keeping state data is hard, but not as hard as multithreading. This gives pseudo-parallelism by simply not delaying with each command, only once per loop to regulate the framerate.
