#rm -rf bin/classes gen && ant -f pre-build.xml && ant debug && mv bin/JustIn-debug.apk bin/JustIn.apk
ant -f pre-build.xml && ant debug && mv bin/JustIn-debug.apk bin/JustIn.apk

