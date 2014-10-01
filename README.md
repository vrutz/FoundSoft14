FoundSoft14
===========

Repo for all the programming assignements for the Foundations of Software class
at EPFL.
e-mail for submissions : foundsoft2014@gmail.com

Members of the team
-------------------
- Damien Engels (paullepoulpe)
- Valentin Rutz (ValentinRutz)
- Valerian Pittet (vtpittet)

Useful Sbt Commands
-------------------

Generate intellij configuration files
```shell
sbt> gen-idea
```

Autocompile and launch tests on file changes
```shell
sbt> ~ ;compile ;test
```

Create jar file for submission (submission\*\*\*.jar)
```shell
sbt> package-for-submission Engels Rutz Pittet
```


