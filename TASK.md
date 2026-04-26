Your task is to update Skript with this changes:
# 1. URL scripts
- Loading skript from url with sk reload URL, or sk disable/enable URL
- Loading with expressions "load" "reload" "unload"
- Option in the skript config, disabled by default
# 2. Error checking with load and reload expressions
A syntax like this would work great i think
```skript
command /reloadOnline:
  trigger:
    if reload script "https://levikk.s3.pl-waw.scw.cloud/test.sk" does not have errors:
      send "Successfully reloaded."
    else:
      send "&cErrors during reloading."
      loop errors:
        send "%loop-value%"  
```
Also read README.md so you know how to run tests.