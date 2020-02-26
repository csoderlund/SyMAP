#!/usr/bin/perl
use warnings;
use strict;

# Reads a fasta file and prints out the lengths in ascending order
# It then prints out the min_len to use to limit symap to a given number of sequence
#   for a draft sequencing project.

my @lenVec;

print "Read $ARGV[0] and print sorted lengths\n"; 
chgFasta($ARGV[0]);
findCutoff();

sub chgFasta {
	my $file=shift;	
	open(IN,"<",$file) || die "Cannot open $file!\n$!\nexiting...\n";
	
	my $len=0;
	my $cnt=0;
	
	while(<IN>){
		my $line=$_;
		if($line=~m/>/){
			if ($len>0) {
				$lenVec[$cnt++] = $len;
			}
			$len=0;
		}else{
			chop $line;
			$len += length $line;
		}
	}
	if ($len>0) {$lenVec[$cnt++] = $len;}
	print "Read $cnt sequences\n";
}

sub findCutoff {
	my @sorted = reverse sort {$a <=> $b} @lenVec;
	my $cnt=0;
	my $num=0;
	my %min_len = (10, 0, 20, 0, 30,0, 40, 0, 50, 0, 60, 0, 70, 0, 80, 0, 90, 0, 100,0);
    
	print "\nLengths:\n";
	for (my $i=0; $i<scalar @sorted; $i++) {
		$cnt++;
        printf "%4d %8d\n", $cnt, $sorted[$i];
        
        foreach $num (keys %min_len) {
        	if ($cnt==$num) {$min_len{$num}=$sorted[$i]; last;}
        }
    }
    print "\nValues for min_len (assuming no duplicate lengths):\n";
    printf "%5s %8s\n", "#Seqs", "min_len";
    foreach $num (sort {$a<=>$b} keys  %min_len) {
    	last if ($min_len{$num}==0);
    	printf "%5d %8d\n", $num, $min_len{$num};
    }
}


