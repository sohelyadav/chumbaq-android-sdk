# Chumbaq BOM

`chumbaq-bom` aligns versions across Chumbaq SDK modules.

## Usage

```kotlin
dependencies {
	implementation(platform("com.chumbaq:chumbaq-bom:0.1.0"))

	implementation("com.chumbaq:chumbaq-core")
	implementation("com.chumbaq:chumbaq-push")
	implementation("com.chumbaq:chumbaq-banners")
	implementation("com.chumbaq:chumbaq-messages")
}
```

Replace `0.1.0` with the version you want to consume.

