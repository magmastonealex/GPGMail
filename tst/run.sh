~/android-sdk-linux/platform-tools/adb backup -noapk me.alexroth.gpgmail
dd if=backup.ab bs=24 skip=1 > myback.db
printf "\x1f\x8b\x08\x00\x00\x00\x00\x00" |cat - myback.db |gzip -dc > back.tar
tar xvf back.tar
sqlite3 apps/me.alexroth.gpgmail/db/Mail.db
rm -r apps back.tar myback.db backup.db
