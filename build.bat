rmdir /s /q "build"
rmdir /s /q "AnimeHayProvider\build"
rmdir /s /q "KKPhimProvider\build"
rmdir /s /q "OPhimProvider\build"
rmdir /s /q "PhimMoiChillProvider\build"
rmdir /s /q "PhimNguonCProvider\build"

gradlew make makePluginsJson
