rmdir /s /q "build"
rmdir /s /q "KKPhimProvider\build"
rmdir /s /q "OPhimProvider\build"
rmdir /s /q "PhimNguonCProvider\build"

gradlew make makePluginsJson
