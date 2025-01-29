---
slug: /
sidebar_position: 1
---

# Overview

Compose Router is a navigation builder for Jetpack Compose. The project is Multiplatform, but bindings only exist for Android right now.

The goal of this library is to provide a simple yet flexible DSL for managing navigation in Compose. Compose Router introduces a clear distinction between **Stack** and **Tab** navigation, two common navigation patterns.

Compose Router is built around the assumption that a navigation graph is often hierarchial. Creating a nested structure that exists _within_ the composition makes scoping shared objects and UI to sections of your navigation graph feel natural.
