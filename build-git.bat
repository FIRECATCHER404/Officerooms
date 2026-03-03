@echo off
if not exist .git (
  git init
)
git add .
