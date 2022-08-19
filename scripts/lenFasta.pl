#!/usr/bin/perl
use warnings;
use strict;

# Reads a fasta file and prints out the lengths in ascending order
# It then prints out the min_len to use to limit symap to a given number of sequence
#   for a draft sequencing project.
# CAS512 update to print ">" line
 
my %lenMap;

print "Read $ARGV[0] and print sorted lengths\n"; 
chgFasta($ARGV[0]);
findCutoff();

sub chgFasta {
	my $file=shift;	
	open(IN,"<",$file) || die "Cannot open $file!\n$!\nexiting...\n";
	
	my $len=0;
	my $cnt=0;
	my $name="";
	
	while(<IN>){
		my $line=$_;
		chop $line;
		if($line=~m/>/){
			if ($len>0) {
				$lenMap{$name} = $len;
			}
			$name = $line;
			$len=0;
			$cnt++;
		} else {
			$len += length $line;
		}
	}
	if ($len>0) {$lenMap{$name} = $len;}
	print "Read $cnt sequences\n";
}

sub findCutoff {
	my $cnt=0;
	my @lenVec; 
	printf "%4s %9s  %s\n", "N", "Length", "Seqid";
	foreach my $name (sort {$lenMap{$b} <=> $lenMap{$a}} keys %lenMap) {
		my $len = $lenMap{$name};
		if ($len<40) {last}
		
		$lenVec[$cnt]=$len;
		$cnt++;
        printf "%4d %9d  %s\n", $cnt, $len, $name;
    }
    
    my $max= scalar @lenVec; 
    if ($max>9) {
		my @cutoff = (10, 20, 30, 40,  50,  60,  70,  80,  90,  100); 
	
		print "\nValues for min_len (assuming no duplicate lengths):\n";
		printf "%5s %8s\n", "#Seqs", "min_len";
		for (my $i=0; $i<10; $i++) {
			my $x = $cutoff[$i];
	   
			if ($x>$max) {last;}
	   
			printf "%5d %8d\n", $x, $lenVec[$x-1];
		}
    }
}

