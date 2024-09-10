##Gopher Client for COMP3310 - Assignment 2

#Project Overview
This project implements a Gopher client as specified for the COMP3310 course assignment. The client adheres to the Gopher protocol RFC 1436, directly opening socket connections to the Gopher server, and performing indexing without the use of external libraries.

#Features Implemented
Connects to a Gopher server and fetches the initial directory listing.
Recursively crawls the directory tree.
Downloads and stores text and binary files encountered.
Prints detailed logs to STDOUT, including request timestamps and sent queries.
Counts and reports on directories visited, files retrieved, and records the smallest and largest files.

#Error Handling
The client is robust against several edge cases, including:
*Connection timeouts and interruptions.
*Server responses that deviate from the Gopher protocol.
*Detection of loops to prevent infinite crawling.

#Wireshark Analysis
Network traffic was captured and analyzed using Wireshark to ensure proper protocol adherence. The key interactions captured include:

*The initial TCP handshake confirming the establishment of a connection
*The structured request and response flow conforming to the Gopher protocol.
*Successful closing of connections without residual or half-open states.
A .pcap file containing the traffic capture is included with this submission in zip.

#Issues Encountered
*Lengthy filenames: Filenames exceeding the filesystem limits were truncated.
*Empty file handling: Adjusted the client to not count a single period (.) as the content of the smallest file.

#Further Improvements
Future improvements could include:

*Enhanced handling of various Gopher item types.
*Optimization of the crawling algorithm to reduce redundant server queries.

