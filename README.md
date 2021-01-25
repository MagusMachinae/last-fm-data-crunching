# VIOOH-tech-test

## Problem
Given datasets of songs played and user-data, determine the top 10 songs played in the top 50 sessions
in the dataset, where a session is defined to be a collection of songs played by a given user where the interval between each element is not greater than 20 minutes.

## Usage

Clone the repo with ```git clone https://github.com/MagusMachinae/viooh-tech-test```. All
commands to be executed are provided at the top of the comment block in ```viooh.core```

Due to storage limits on git-hub, it is necessary to download the data set from
http://ocelma.net/MusicRecommendationDataset/lastfm-1K.html
, placing it in ```resources/```, it is useful at this stage to rename the file containing the data of songs played to make the next step less tedious, the readme and code in the namespace assumes it has been renamed to ```song-data.tsv```.

Call ```clojure (data->edn! "resources/song-data.tsv")``` to generate a set of files containing hash-maps of data representing plays of songs sorted by user.

Call ```clojure (generate-user-sessions! user-vector)``` to output a list of files containing session data with a structure of ```{:session-name user-session-# :play-count int :session-data [play & plays]}```

## Notes and Commentary
While I had a strong intuition about how to go about solving the problem, I had difficulty concretely visualising each step, so I decided to start with focusing on IO and data parsing, which would also help me persist the results of each step and get data back that I could use to help reason about what data structures my functions need to operate on by generating output files.

Because of the nature of the problem, I decided to approach it more interactively, grabbing small sections of data to work on in the comment block, and gradually building up the logic to calculate the final count and testing against generated data, naming processes once I had reached a clear boundary at which to mark progress, that gave me a better vantage point to the rest of the solution and improve upon it. For example, generating the files told me that only 647 users are represented in the dataset, so I could adjust my functions for pulling the users out of the source file to limit the result-set. Seperating the output into files also helped the process of collecting them into sessions, as pre-sorting the files meant that a test on users would be redundant.

The next step is relatively simple, and about half of the code for it is already complete, as I need to re-accumulate the data into a single collection, so I can sort by value of ```:play-count``` and take the 50 longest sessions. From there, I can write a function to return the count number of occurences of a given song, and apply the same filtering process to the previous step to take the top 10.

When it came to choosing my libraries, tick seemed an obvious choice because of its interval calculus for computing times, and I needed a .csv parsing library to handle reading and writing .tsv, but aside from that, I felt that the core language features and tactical application of hash-maps would be more than enough to provide an easy solution. 
## TODO



## License

Copyright © 2021 FIXME

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
