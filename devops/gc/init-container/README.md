## About The Solution

This solution is created purely as a temporary measure to maintain the proper functioning of the Indexer. This solution will cease to exist after the implementation of task GONRG-8825-Expiration time for Mapper\Driver configurations OR Configuration lifecycle events.
The init container checks for the presence of at least one data tenant in the partition before starting the main container, enabling it to obtain the correct configuration for the Indexer upon its launch.
