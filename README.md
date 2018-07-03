# Event bus

Simple event bus utilizing ASM written in Kotlin

## What?

An event bus, simple. Register listeners, pass event instance and listener methods listening to given event
instance will be invoked.

## Why?

Speed.

## Limitations

Listener methods cannot be static/non-public. Only methods found from class of given instance will be used.