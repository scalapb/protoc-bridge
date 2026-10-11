{pkgs ? import <nixpkgs> {
  config = {
    packageOverrides = pkgs: {
      sbt = pkgs.sbt.override { jre = pkgs.openjdk17; };
    };
  };
}} :
pkgs.mkShell {
  buildInputs = [
    pkgs.sbt
    pkgs.openjdk17

    # keep this line if you use bash
    pkgs.bashInteractive
  ];
}
