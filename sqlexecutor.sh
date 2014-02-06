#!/bin/sh
if [ -n "${SQL_EXEC_HOME:+x}" ] 
then
  echo SQL_EXEC_HOME set to $SQL_EXEC_HOME
  export JAVA_CLASSPATH=.:./lib/postgresql-9.2-1000.jdbc4.jar
  java -cp  $JAVA_CLASSPATH SQLExecutor $*
else
  echo SQL_EXEC_HOME env var is not set
fi


