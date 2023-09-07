/**
 * @author VISTALL
 * @since 07/09/2023
 */
open module consulo.apache.subversion {
  requires consulo.ide.api;

  requires svnkit;

  requires jakarta.xml.bind;

  requires java.sql;

  requires sqljet;

  requires jsch.agentproxy.core;

  // TODO remove in future
  requires consulo.ide.impl;
  
  // TODO remove in future
  requires forms.rt;
  requires java.desktop;
}