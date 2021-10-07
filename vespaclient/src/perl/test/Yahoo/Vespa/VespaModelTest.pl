# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

use Test::More;
use Yahoo::Vespa::Mocks::VespaModelMock;

BEGIN {
    use_ok( 'Yahoo::Vespa::VespaModel' );
    *VespaModel:: = *Yahoo::Vespa::VespaModel:: ;
}
require_ok( 'Yahoo::Vespa::VespaModel' );

&testGetSocketForService();
&testVisitServices();

done_testing();

exit(0);

sub testGetSocketForService {
    my $sockets = VespaModel::getSocketForService(
            type => 'container-clustercontroller', tag => 'state');
    my ($host, $port) = ($$sockets[0]->{'host'}, $$sockets[0]->{'port'});
    is( $host, 'testhost.yahoo.com', "Host for state API" );
    is( $port, 19050, 'Port for state API' );
    $sockets = VespaModel::getSocketForService(
            type => 'container-clustercontroller', tag => 'admin');
    ($host, $port) = ($$sockets[0]->{'host'}, $$sockets[0]->{'port'});
    is( $host, 'testhost.yahoo.com', "Host for state API" );
    is( $port, 19102, 'Port for state API' );
    $sockets = VespaModel::getSocketForService(
            type => 'container-clustercontroller', tag => 'http');
    ($host, $port) = ($$sockets[0]->{'host'}, $$sockets[0]->{'port'});
    is( $port, 19100, 'Port for state API' );

    $sockets = VespaModel::getSocketForService(
            type => 'distributor', index => 0);
    ($host, $port) = ($$sockets[0]->{'host'}, $$sockets[0]->{'port'});
    is( $host, 'testhost.yahoo.com', 'host for distributor 0' );
}

my @services;

sub serviceCallback {
    my ($info) = @_;
    push @services, "Name($$info{'name'}) Type($$info{'type'}) "
                  . "Cluster($$info{'cluster'}) Host($$info{'host'}) "
                  . "Index($$info{'index'})";
}

sub testVisitServices {
    @services = ();
    VespaModel::visitServices(\&serviceCallback);
    my $expected = <<EOS;
Name(storagenode3) Type(storagenode) Cluster(books) Host(testhost.yahoo.com) Index(0)
Name(storagenode3) Type(storagenode) Cluster(books) Host(other.host.yahoo.com) Index(1)
Name(container-clustercontroller) Type(container-clustercontroller) Cluster(cluster-controllers) Host(testhost.yahoo.com) Index(0)
Name(distributor2) Type(distributor) Cluster(music) Host(testhost.yahoo.com) Index(0)
Name(distributor2) Type(distributor) Cluster(music) Host(other.host.yahoo.com) Index(1)
Name(storagenode2) Type(storagenode) Cluster(music) Host(other.host.yahoo.com) Index(0)
EOS
    chomp $expected;
    is ( join("\n", @services), $expected, "Services visited correctly" );
}
