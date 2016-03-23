### Changelog

#### Fuse Patch 2.2.0

**Tasks**

* [#137][137] Consolidate core & full feature pack
* [#138][138] Upgrade to wildfly-10.0.0

For details see [2.2.0 tasks](https://github.com/wildfly-extras/fuse-patch/issues?q=milestone%3A"2.2.0"+label%3Atask)

[137]: https://github.com/wildfly-extras/fuse-patch/issues/137
[138]: https://github.com/wildfly-extras/fuse-patch/issues/138

#### Fuse Patch 2.1.1

**Bugs**

* [#134][134] Incompatible metadata change in version 2.0
* [#136][136] Release prepare does not install artefacts in local repo

For details see [2.1.1 bugs](https://github.com/wildfly-extras/fuse-patch/issues?q=milestone%3A"2.1.1"+label%3Abug)

[134]: https://github.com/wildfly-extras/fuse-patch/issues/134
[136]: https://github.com/wildfly-extras/fuse-patch/issues/136

#### Fuse Patch 2.1.0

**Features**

* [#131][131] Provide a feature pack that runs on wildfly-core

For details see [2.1.0 features](https://github.com/wildfly-extras/fuse-patch/issues?q=milestone%3A"2.1.0"+label%3Afeature)

**Bugs**

* [#128][128] Dependency on aether not transitively reachable through core
* [#132][132] Invalid module definition for org.eclipse.aether

For details see [2.1.0 bugs](https://github.com/wildfly-extras/fuse-patch/issues?q=milestone%3A"2.1.0"+label%3Abug)

[131]: https://github.com/wildfly-extras/fuse-patch/issues/131
[128]: https://github.com/wildfly-extras/fuse-patch/issues/128
[132]: https://github.com/wildfly-extras/fuse-patch/issues/132

#### Fuse Patch 2.0.0

**Features**

* [#38][38] Provide remote accessible repository
* [#105][105] Protect remote repository content with security roles
* [#106][106] Provide maven repository as back store option
* [#122][122] Configure patch tool client through config file

For details see [2.0.0 features](https://github.com/wildfly-extras/fuse-patch/issues?q=milestone%3A"2.0.0"+label%3Afeature)

**Tasks**

* [#77][77] Add changelog and release notes
* [#107][107] Migrate parameters to structured metadata 
* [#110][110] Combine oneoff upload with base patch content
* [#114][114] Consistently use Patch instead of Package in API
* [#126][126] Document repository flavours, roles, client config 

For details see [2.0.0 tasks](https://github.com/wildfly-extras/fuse-patch/issues?q=milestone%3A"2.0.0"+label%3Atask)

**Bugs**

* [#57][57] Already existing paths may incorrectly get removed on update
* [#100][100] Directories not deleted during update
* [#112][112] Repository always puts full patch content in smart patch
* [#116][116] Temporary file for smart patch content not deleted
* [#118][118] RepositoryClient does not unwrap WebServiceException
* [#124][124] Command line roles are ignored

For details see [2.0.0 bugs](https://github.com/wildfly-extras/fuse-patch/issues?q=milestone%3A"2.0.0"+label%3Abug)

[38]: https://github.com/wildfly-extras/fuse-patch/issues/38
[105]: https://github.com/wildfly-extras/fuse-patch/issues/105
[106]: https://github.com/wildfly-extras/fuse-patch/issues/106
[122]: https://github.com/wildfly-extras/fuse-patch/issues/122
[77]: https://github.com/wildfly-extras/fuse-patch/issues/77
[107]: https://github.com/wildfly-extras/fuse-patch/issues/107
[110]: https://github.com/wildfly-extras/fuse-patch/issues/110
[114]: https://github.com/wildfly-extras/fuse-patch/issues/114
[126]: https://github.com/wildfly-extras/fuse-patch/issues/126
[57]: https://github.com/wildfly-extras/fuse-patch/issues/57
[100]: https://github.com/wildfly-extras/fuse-patch/issues/100
[112]: https://github.com/wildfly-extras/fuse-patch/issues/112
[116]: https://github.com/wildfly-extras/fuse-patch/issues/116
[118]: https://github.com/wildfly-extras/fuse-patch/issues/118
[124]: https://github.com/wildfly-extras/fuse-patch/issues/124

#### Fuse Patch 1.6.2

**Tasks**

* [#97][97] Display warning when adding duplicate packages to repository

For details see [1.6.2 tasks](https://github.com/wildfly-extras/fuse-patch/issues?q=milestone%3A"1.6.2"+label%3Atask)

[97]: https://github.com/wildfly-extras/fuse-patch/issues/97

#### Fuse Patch 1.6.1

**Bugs**

* [#96][96] fuseconfig.sh points to wrong module identity

For details see [1.6.1 bugs](https://github.com/wildfly-extras/fuse-patch/issues?q=milestone%3A"1.6.1"+label%3Abug)

[96]: https://github.com/wildfly-extras/fuse-patch/issues/96

#### Fuse Patch 1.6.0

**Features**

* [#93][93] Provide fuse-patch as feature pack

For details see [1.6.0 features](https://github.com/wildfly-extras/fuse-patch/issues?q=milestone%3A"1.6.0"+label%3Afeature)

**Tasks**

* [#91][91] Migrate config core functionality to fuse-patch

For details see [1.6.0 tasks](https://github.com/wildfly-extras/fuse-patch/issues?q=milestone%3A"1.6.0"+label%3Atask)

**Bugs**

* [#94][94] Repository root path cannot contain spaces

For details see [1.6.0 bugs](https://github.com/wildfly-extras/fuse-patch/issues?q=milestone%3A"1.6.0"+label%3Abug)

[93]: https://github.com/wildfly-extras/fuse-patch/issues/93
[91]: https://github.com/wildfly-extras/fuse-patch/issues/91
[94]: https://github.com/wildfly-extras/fuse-patch/issues/94

#### Fuse Patch 1.5.0

**Features**

* [#53][53] Add Windows shell script to the distributions
* [#56][56] Allow overlapping file sets on --add if equal
* [#66][66] Add support for --remove
* [#78][78] Add support for --uninstall
* [#79][79] Add support for --query-server-paths

For details see [1.5.0 features](https://github.com/wildfly-extras/fuse-patch/issues?q=milestone%3A"1.5.0"+label%3Afeature)

**Tasks**

* [#69][69] Restore usage of URL in Repository API
* [#71][71] Remove @ symbol placeholders from standalone fusepatch.bat
* [#73][73] Restore usage of fusepatch script when building standalone distro
* [#80][80] Improve help screen content for subcommands
* [#82][82] Review logging strategy
* [#85][85] Replace all references to File with URL in the public API

For details see [1.5.0 tasks](https://github.com/wildfly-extras/fuse-patch/issues?q=milestone%3A"1.5.0"+label%3Atask)

**Bugs**

* [#67][67] Cannot build standalone distro on windows
* [#83][83] Downgrading does not remove higher version metadata
* [#87][87] Core test suite fails on Windows
* [#89][89] Integration tests fail on AIX

For details see [1.5.0 bugs](https://github.com/wildfly-extras/fuse-patch/issues?q=milestone%3A"1.5.0"+label%3Abug)

[53]: https://github.com/wildfly-extras/fuse-patch/issues/53
[56]: https://github.com/wildfly-extras/fuse-patch/issues/56
[66]: https://github.com/wildfly-extras/fuse-patch/issues/66
[78]: https://github.com/wildfly-extras/fuse-patch/issues/78
[79]: https://github.com/wildfly-extras/fuse-patch/issues/79
[69]: https://github.com/wildfly-extras/fuse-patch/issues/69
[71]: https://github.com/wildfly-extras/fuse-patch/issues/71
[73]: https://github.com/wildfly-extras/fuse-patch/issues/73
[80]: https://github.com/wildfly-extras/fuse-patch/issues/80
[82]: https://github.com/wildfly-extras/fuse-patch/issues/82
[85]: https://github.com/wildfly-extras/fuse-patch/issues/85
[67]: https://github.com/wildfly-extras/fuse-patch/issues/67
[83]: https://github.com/wildfly-extras/fuse-patch/issues/83
[87]: https://github.com/wildfly-extras/fuse-patch/issues/87
[89]: https://github.com/wildfly-extras/fuse-patch/issues/89

#### Fuse Patch 1.4.0

**Tasks**

* [#63][63] Move installer base from fuse-eap to fuse-patch

For details see [1.4.0 tasks](https://github.com/wildfly-extras/fuse-patch/issues?q=milestone%3A"1.4.0"+label%3Atask)

[63]: https://github.com/wildfly-extras/fuse-patch/issues/63

#### Fuse Patch 1.3.1

**Features**

* [#50][50] Provide documentation about used concepts
* [#58][58] All overlapping files on --add with --force

For details see [1.3.1 features](https://github.com/wildfly-extras/fuse-patch/issues?q=milestone%3A"1.3.1"+label%3Afeature)

**Tasks**

* [#51][51] Align code with documented concept terms

For details see [1.3.1 tasks](https://github.com/wildfly-extras/fuse-patch/issues?q=milestone%3A"1.3.1"+label%3Atask)

**Bugs**

* [#54][54] Main may swallow RuntimeExceptions without user feedback
* [#59][59] Layer that contains fuse-patch may get removed

For details see [1.3.1 bugs](https://github.com/wildfly-extras/fuse-patch/issues?q=milestone%3A"1.3.1"+label%3Abug)

[50]: https://github.com/wildfly-extras/fuse-patch/issues/50
[58]: https://github.com/wildfly-extras/fuse-patch/issues/58
[51]: https://github.com/wildfly-extras/fuse-patch/issues/51
[54]: https://github.com/wildfly-extras/fuse-patch/issues/54
[59]: https://github.com/wildfly-extras/fuse-patch/issues/59

#### Fuse Patch 1.3.0

**Features**

* [#12][12] Prevent target content override not managed by fusepatch
* [#21][21] Enforce non overlapping patch sets on --add 
* [#24][24] Add support for one-off patches
* [#35][35] Add support to print the audit log
* [#45][45] Add support for package dependencies

For details see [1.3.0 features](https://github.com/wildfly-extras/fuse-patch/issues?q=milestone%3A"1.3.0"+label%3Afeature)

**Tasks**

* [#32][32] Add user documentation
* [#39][39] Review concurrent access to server/repository
* [#48][48] Be more lenient about Error: Attempt to add an already existing file

For details see [1.3.0 tasks](https://github.com/wildfly-extras/fuse-patch/issues?q=milestone%3A"1.3.0"+label%3Atask)

**Bugs**

* [#33][33] No logging in wildfly distro
* [#40][40] Post install commands cannot contain spaces
* [#42][42] Output from post install commands not visible

For details see [1.3.0 bugs](https://github.com/wildfly-extras/fuse-patch/issues?q=milestone%3A"1.3.0"+label%3Abug)

[12]: https://github.com/wildfly-extras/fuse-patch/issues/12
[21]: https://github.com/wildfly-extras/fuse-patch/issues/21
[24]: https://github.com/wildfly-extras/fuse-patch/issues/24
[35]: https://github.com/wildfly-extras/fuse-patch/issues/35
[45]: https://github.com/wildfly-extras/fuse-patch/issues/45
[32]: https://github.com/wildfly-extras/fuse-patch/issues/32
[39]: https://github.com/wildfly-extras/fuse-patch/issues/39
[48]: https://github.com/wildfly-extras/fuse-patch/issues/48
[33]: https://github.com/wildfly-extras/fuse-patch/issues/33
[40]: https://github.com/wildfly-extras/fuse-patch/issues/40
[42]: https://github.com/wildfly-extras/fuse-patch/issues/42

#### Fuse Patch 1.2.2

**Tasks**

* [#20][20] Revisit server/repository meta data
* [#27][27] Make fuse-patch public for documentation access
* [#29][29] Update license headers

For details see [1.2.2 tasks](https://github.com/wildfly-extras/fuse-patch/issues?q=milestone%3A"1.2.2"+label%3Atask)

[20]: https://github.com/wildfly-extras/fuse-patch/issues/20
[27]: https://github.com/wildfly-extras/fuse-patch/issues/27
[29]: https://github.com/wildfly-extras/fuse-patch/issues/29

#### Fuse Patch 1.2.0

**Features**

* [#10][10] Add wildfly distro to standalone distro
* [#11][11] Add support for adding archives to the repository
* [#13][13] Add support for post-install command execution

For details see [1.2.0 features](https://github.com/wildfly-extras/fuse-patch/issues?q=milestone%3A"1.2.0"+label%3Afeature)

**Bugs**

* [#22][22] Support versions of type 1.0.0.redhat-SNAPSHOT

For details see [1.2.0 bugs](https://github.com/wildfly-extras/fuse-patch/issues?q=milestone%3A"1.2.0"+label%3Abug)

[10]: https://github.com/wildfly-extras/fuse-patch/issues/10
[11]: https://github.com/wildfly-extras/fuse-patch/issues/11
[13]: https://github.com/wildfly-extras/fuse-patch/issues/13
[22]: https://github.com/wildfly-extras/fuse-patch/issues/22

#### Fuse Patch 1.1.0

**Features**

* [#3][3] Add patch tool info to generated metadata
* [#4][4] Add notion of target server and patch pool
* [#5][5] Add support to infer the server target path
* [#6][6] Add support to infer the patch pool URL
* [#7][7] Add distro that can run in wildfly

For details see [1.1.0 features](https://github.com/wildfly-extras/fuse-patch/issues?q=milestone%3A"1.1.0"+label%3Afeature)

[3]: https://github.com/wildfly-extras/fuse-patch/issues/3
[4]: https://github.com/wildfly-extras/fuse-patch/issues/4
[5]: https://github.com/wildfly-extras/fuse-patch/issues/5
[6]: https://github.com/wildfly-extras/fuse-patch/issues/6
[7]: https://github.com/wildfly-extras/fuse-patch/issues/7