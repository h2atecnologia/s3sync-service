package main

import (
	"crypto/md5"
	"fmt"
	"io/ioutil"
	"os"
	"path/filepath"
	"regexp"
)

func checkIfInList(item string, list []string) bool {
	for _, i := range list {
		if i == item {
			return true
		}
	}

	return false
}

func checkIfExcluded(path string, exclusions []string) bool {
	var excluded bool
	excluded = false

	for _, exclusion := range exclusions {
		re := regexp.MustCompile(exclusion)
		if re.FindAll([]byte(path), -1) != nil {
			excluded = true
		}
	}

	return excluded
}

// FilePathWalkDir walks throught the directory and all subdirectories returning list of files for upload and list of files to be deleted from S3
func FilePathWalkDir(root string, exclusions []string, awsItems map[string]string, bucketPath string) ([]string, []string, error) {
	var uploadFiles []string
	var deleteKeys []string
	var localS3Keys []string
	var filescount int
	ch := make(chan string)

	err := filepath.Walk(root, func(path string, info os.FileInfo, err error) error {
		if err != nil {
			return err
		}

		if !info.IsDir() {
			excluded := checkIfExcluded(path, exclusions)
			if excluded {
				logger.Printf("skipping without errors: %+v \n", path)
			} else {
				filescount++
				s3Key := generateS3Key(bucketPath, root, path)
				localS3Keys = append(localS3Keys, s3Key)
				checksumRemote, _ := awsItems[s3Key]
				go compareChecksum(path, checksumRemote, ch)
			}
		}
		return nil
	})

	// Wait for checksums to be compared
	var filename string
	for i := 0; i < filescount; i++ {
		filename = <-ch
		if len(filename) > 0 {
			uploadFiles = append(uploadFiles, filename)
		}
	}

	// Generate a list of deleted files
	for key := range awsItems {
		if !checkIfInList(key, localS3Keys) {
			deleteKeys = append(deleteKeys, key)
		}
	}

	return uploadFiles, deleteKeys, err
}

func compareChecksum(filename string, checksumRemote string, ch chan<- string) {
	if checksumRemote == "" {
		ch <- filename
		return
	}

	contents, err := ioutil.ReadFile(filename)
	if err == nil {
		sum := md5.Sum(contents)
		sumString := fmt.Sprintf("%x", sum)
		// checksums don't match, mark for upload
		if sumString != checksumRemote {
			ch <- filename
			return
		}
		// Files matched
		ch <- ""
		return
	}
}