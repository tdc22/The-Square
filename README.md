# The Square
"The Square" is a puzzle-platformer I created in less than 2 weeks using my game (rendering and physics) engine [JAwesomeEngine](https://github.com/tdc22/JAwesomeEngine) for and inspired by [Happie's Multidimensionality Challenge](https://www.reddit.com/r/TheHappieMakers/comments/4gb7f7/game_dev_challenge_multidimensionality/).

## Play Now
Requirements:
* Java 8 or higher
* at least OpenGL 3.3
* Windows or Linux (Mac or other platforms are untested but could work)

Just download the [TheSquare.zip](https://github.com/tdc22/The-Square/blob/master/TheSquare.zip?raw=true), extract it and run the file "TheSquare.jar" either directly or via command line using:  
```java -jar TheSquare.jar```

If you have a question or run into a problem, feel free to message me.

## Controls
* WASD: movement
* Keyboard: jump
* Escape: close game

## Source
If you want to play around with the source code or build your own levels you can setup the source to compile and run locally. However this can be a bit tricky.  
First you have to setup [JAwesomeEngine](https://github.com/tdc22/JAwesomeEngine) (as described there) and run some tests from the included "Awesome Test" to verify that everything works. Then pull this repository via git (or download the source directly) and link all three JAwesomeEngine-Projects. In Eclipse you can do this by right-clicking the "The Square"-Project -> Properties -> Java Build Path -> Projects and then adding "JAwesomeBase", "JAwesomeEngine" and "JAwesomePhysics". As soon as this is done you should be able to start it by executing Start.java.
