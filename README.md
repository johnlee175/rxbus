# rxbus
a event bus implements by rxjava/rxandroid
---  
In proguard:
```
-keepclassmembers class * {
    @org.rxbus.Subscribe <methods>;
}
```
