# ExoPlayer-CustomizedBuffer
Project to test the buffering of ExoPlayer in various network conditions through changing different buffering input values.




*ExoPlayer documentation*

## Variable inputs are as follows:

#### 1. connectTimeoutMillis

*The connection timeout that should be used when requesting remote data, in milliseconds. A timeout of zero is interpreted as an infinite timeout.*

previously was 8000 ms -> now suggested 0 ms

#### 2. readTimeOutMillis

*The read timeout that should be used when requesting remote data, in milliseconds. A timeout of zero is interpreted as an infinite timeout.*

previously was 8000 ms -> now suggested 0 ms

#### 3. default buffer segment size

*A default size in bytes for an individual allocation that forms part of a larger buffer.*

public static final int DEFAULT_BUFFER_SEGMENT_SIZE = 64 * 1024; //65536

Exoplayer uses this base value to calculate different buffer sizes according to track type.
For audio tracks this value is doubled by 200. 


#### 4. minBufferMs

*The minimum duration of media that the player will attempt to ensure is buffered at all times, in milliseconds.*

previously was 50,000 -> now suggested 15,000 (needs more testing)

#### 5. maxBufferMs

*The maximum duration of media that the player will attempt to buffer, in milliseconds.*

previously was 50,000 -> (needs more testing to check if shall be changed)

#### 6. bufferForPlaybackMs

*The duration of media that must be buffered for playback to start or resume following a user action such as a seek, in milliseconds.*

2500

#### 7. bufferForPlaybackAfterRebuffer
*The default duration of media that must be buffered for playback to resume after a rebuffer, in milliseconds. A rebuffer is defined to be caused by buffer depletion rather than a user action.*

5000
